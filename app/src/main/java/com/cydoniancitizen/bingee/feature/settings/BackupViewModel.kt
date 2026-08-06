package com.cydoniancitizen.bingee.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.data.importexport.BackupDataStore
import com.cydoniancitizen.bingee.data.importexport.BackupExporter
import com.cydoniancitizen.bingee.data.importexport.BackupFailureKind
import com.cydoniancitizen.bingee.data.importexport.BackupFileGateway
import com.cydoniancitizen.bingee.data.importexport.BackupParseResult
import com.cydoniancitizen.bingee.data.importexport.BackupPreview
import com.cydoniancitizen.bingee.data.importexport.BackupValidationResult
import com.cydoniancitizen.bingee.data.importexport.BackupValidator
import com.cydoniancitizen.bingee.data.importexport.ValidatedBackupPlan
import com.cydoniancitizen.bingee.domain.background.BackgroundWorkScheduler
import com.cydoniancitizen.bingee.domain.repository.ReleaseNotificationPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class BackupOperation {
    IDLE,
    SAVING,
    SHARING,
    READING,
    VALIDATING,
    PREVIEW_READY,
    RESTORING,
    SUCCESS,
    FAILURE
}

internal data class BackupUiState(
    val operation: BackupOperation = BackupOperation.IDLE,
    val preview: BackupPreview? = null,
    val failure: BackupFailureKind? = null,
    val schedulingWarning: Boolean = false,
    val hasAnimePreserved: Boolean = false
)

@HiltViewModel
internal class BackupViewModel @Inject constructor(
    private val exporter: BackupExporter,
    private val dataStore: BackupDataStore,
    private val fileGateway: BackupFileGateway,
    private val preferencesRepository: ReleaseNotificationPreferencesRepository,
    private val scheduler: BackgroundWorkScheduler
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = mutableUiState.asStateFlow()
    private val operationLock = Mutex()
    private var pendingPlan: ValidatedBackupPlan? = null

    fun saveTo(uri: Uri) {
        if (!operationLock.tryLock()) return
        viewModelScope.launch {
            try {
                mutableUiState.update { it.copy(operation = BackupOperation.SAVING, failure = null) }
                val backup = exporter.export()
                val failure = fileGateway.write(uri, backup.bytes)
                mutableUiState.update {
                    it.copy(
                        operation = if (failure == null) BackupOperation.SUCCESS else BackupOperation.FAILURE,
                        failure = failure
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableUiState.update {
                    it.copy(operation = BackupOperation.FAILURE, failure = BackupFailureKind.WRITE_FAILED)
                }
            } finally {
                operationLock.unlock()
            }
        }
    }

    fun share() {
        if (!operationLock.tryLock()) return
        viewModelScope.launch {
            try {
                mutableUiState.update { it.copy(operation = BackupOperation.SHARING, failure = null) }
                val backup = exporter.export()
                val failure = fileGateway.share(backup.bytes)
                mutableUiState.update {
                    it.copy(
                        operation = if (failure == null) BackupOperation.SUCCESS else BackupOperation.FAILURE,
                        failure = failure
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableUiState.update {
                    it.copy(operation = BackupOperation.FAILURE, failure = BackupFailureKind.WRITE_FAILED)
                }
            } finally {
                operationLock.unlock()
            }
        }
    }

    fun importFrom(uri: Uri) {
        if (!operationLock.tryLock()) return
        viewModelScope.launch {
            try {
                pendingPlan = null
                mutableUiState.update { it.copy(operation = BackupOperation.READING, preview = null, failure = null) }
                val parsed = fileGateway.read(uri)
                if (parsed is BackupParseResult.Failure) {
                    fail(parsed.failure.kind)
                    return@launch
                }
                mutableUiState.update { it.copy(operation = BackupOperation.VALIDATING) }
                val document = (parsed as BackupParseResult.Success).document
                val validation = BackupValidator.validate(document)
                if (validation is BackupValidationResult.Failure) {
                    fail(validation.failure.kind)
                    return@launch
                }
                val plan = (validation as BackupValidationResult.Success).plan
                pendingPlan = plan
                val preview = BackupValidator.preview(plan, dataStore.currentLibraryCount())
                mutableUiState.update { it.copy(operation = BackupOperation.PREVIEW_READY, preview = preview) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                pendingPlan = null
                fail(BackupFailureKind.UNREADABLE)
            } finally {
                operationLock.unlock()
            }
        }
    }

    fun cancelPreview() {
        if (mutableUiState.value.operation == BackupOperation.PREVIEW_READY) {
            pendingPlan = null
            mutableUiState.value = BackupUiState()
        }
    }

    fun confirmRestore() {
        val plan = pendingPlan ?: return
        if (!operationLock.tryLock()) return
        viewModelScope.launch {
            try {
                mutableUiState.update { it.copy(operation = BackupOperation.RESTORING, failure = null) }
                dataStore.restore(plan)
                pendingPlan = null
                var warning = false
                try {
                    scheduler.reconcileNotificationWork(preferencesRepository.preferences.first().enabled)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    warning = true
                }
                val data = plan.document.data
                val containsAnime = data.animeDetails.isNotEmpty() ||
                    data.animeProgress.isNotEmpty() ||
                    data.media.any {
                        it.primaryRef.source == com.cydoniancitizen.bingee.core.model.MediaSource.JIKAN ||
                            it.mediaType == com.cydoniancitizen.bingee.core.model.MediaType.ANIME
                    }
                mutableUiState.update {
                    it.copy(
                        operation = BackupOperation.SUCCESS,
                        preview = null,
                        schedulingWarning = warning,
                        hasAnimePreserved = containsAnime,
                        failure = if (warning) BackupFailureKind.SCHEDULING_WARNING else null
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                pendingPlan = null
                fail(BackupFailureKind.TRANSACTION_FAILED)
            } finally {
                operationLock.unlock()
            }
        }
    }

    fun dismissFeedback() {
        if (mutableUiState.value.operation == BackupOperation.PREVIEW_READY) return
        mutableUiState.value = BackupUiState()
    }

    private fun fail(kind: BackupFailureKind) {
        mutableUiState.update { it.copy(operation = BackupOperation.FAILURE, preview = null, failure = kind) }
    }
}
