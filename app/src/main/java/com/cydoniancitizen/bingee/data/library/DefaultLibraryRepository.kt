package com.cydoniancitizen.bingee.data.library

import android.database.sqlite.SQLiteException
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.applyLibraryStateAndSort
import com.cydoniancitizen.bingee.core.model.normalizeLibrarySearch
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.LibraryDao
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import com.cydoniancitizen.bingee.data.library.local.RatingDao
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
internal class DefaultLibraryRepository @Inject constructor(
    private val libraryDao: LibraryDao,
    private val ratingDao: RatingDao,
    private val clock: Clock
) : LibraryRepository {
    override fun observeEntries(query: LibraryQuery): Flow<AppResult<List<LibraryEntry>>> {
        val items = libraryDao.observeLibraryItems(
            query.mediaFilter.mediaType,
            query.searchQuery.toSqlLikePattern()
        )
        return combine(
            items,
            libraryDao.observeLibraryProgress(LocalDate.now(clock)),
            ratingDao.observeActiveLibraryRatings()
        ) { rows, progress, ratings ->
            val progressByMedia = progress.associateBy { it.localMediaId }
            val ratingsByMedia = ratings.associateBy { it.localMediaId }
            val entries = rows.map { row ->
                val localMediaId = row.media.localMediaId
                row.toDomain(
                    progressRow = progressByMedia[localMediaId],
                    rating = ratingsByMedia[localMediaId]
                )
            }
            applyLibraryStateAndSort(entries, query)
        }.asPersistenceResult { it }
    }

    override fun observeEntryCount(): Flow<AppResult<Int>> =
        libraryDao.observeLibraryEntryCount().asPersistenceResult { it }

    override fun observeEntry(ref: ExternalMediaRef): Flow<AppResult<LibraryEntry?>> = libraryDao
        .observeLibraryItem(ref.source, ref.normalizedExternalId())
        .asPersistenceResult { row -> row?.toDomain(preferredRef = ref.normalized()) }

    override fun observeMembershipRefs(): Flow<AppResult<Set<ExternalMediaRef>>> =
        libraryDao.observeMembershipRefs().asPersistenceResult { rows ->
            rows.mapTo(linkedSetOf()) { it.toDomain() }
        }

    override suspend fun add(result: MediaSearchResult): AppResult<LibraryEntry> {
        val prepared =
            try {
                val now = clock.instant()
                PreparedLibraryAdd(
                    ref = result.externalRef.normalized(),
                    media = result.toMediaEntity(now),
                    addedAt = now
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                return AppResult.Failure(AppError.InvalidInput)
            } catch (_: Exception) {
                return AppResult.Failure(AppError.Unknown)
            }
        return try {
            val row =
                libraryDao.addToLibrary(
                    candidate = prepared.media,
                    source = prepared.ref.source,
                    externalId = prepared.ref.externalId,
                    addedAt = prepared.addedAt
                )
            AppResult.Success(row.toDomain(preferredRef = prepared.ref))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalArgumentException) {
            AppResult.Failure(AppError.CorruptedData)
        } catch (_: IllegalStateException) {
            AppResult.Failure(AppError.CorruptedData)
        } catch (_: SQLiteException) {
            AppResult.Failure(AppError.LocalStorageFailure)
        } catch (_: Exception) {
            AppResult.Failure(AppError.Unknown)
        }
    }

    override suspend fun add(ref: ExternalMediaRef): AppResult<LibraryEntry> =
        withNormalizedExternalId(ref) { externalId ->
            try {
                val row = libraryDao.addExistingToLibrary(ref.source, externalId, clock.instant())
                    ?: return@withNormalizedExternalId AppResult.Failure(AppError.MissingData)
                AppResult.Success(row.toDomain(preferredRef = ExternalMediaRef(ref.source, externalId)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                AppResult.Failure(AppError.CorruptedData)
            } catch (_: IllegalStateException) {
                AppResult.Failure(AppError.CorruptedData)
            } catch (_: SQLiteException) {
                AppResult.Failure(AppError.LocalStorageFailure)
            } catch (_: Exception) {
                AppResult.Failure(AppError.Unknown)
            }
        }

    override suspend fun remove(ref: ExternalMediaRef): AppResult<Unit> = withNormalizedExternalId(ref) { externalId ->
        persistenceRead {
            libraryDao.removeMembership(ref.source, externalId)
            Unit
        }
    }

    override suspend fun isInLibrary(ref: ExternalMediaRef): AppResult<Boolean> =
        withNormalizedExternalId(ref) { externalId ->
            persistenceRead { libraryDao.isInLibrary(ref.source, externalId) }
        }
}

internal fun String.toSqlLikePattern(): String {
    val normalized = normalizeLibrarySearch(this)
    if (normalized.isEmpty()) return "%"
    val escaped = buildString(normalized.length) {
        normalized.forEach { character ->
            if (character == '\\' || character == '%' || character == '_') append('\\')
            append(character)
        }
    }
    return "%$escaped%"
}

private data class PreparedLibraryAdd(val ref: ExternalMediaRef, val media: MediaEntity, val addedAt: Instant)

private fun ExternalMediaRef.normalized(): ExternalMediaRef =
    ExternalMediaRef(source = source, externalId = normalizedExternalId())

private fun ExternalMediaRef.normalizedExternalId(): String =
    externalId.trim().also { require(it.isNotEmpty()) { "External media ID must not be blank" } }

private fun <T, R> Flow<T>.asPersistenceResult(transform: (T) -> R): Flow<AppResult<R>> =
    map<T, AppResult<R>> { value -> AppResult.Success(transform(value)) }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            val error =
                when (throwable) {
                    is IllegalArgumentException,
                    is IllegalStateException -> AppError.CorruptedData
                    is SQLiteException -> AppError.LocalStorageFailure
                    else -> AppError.Unknown
                }
            emit(AppResult.Failure(error))
        }

private suspend fun <T> persistenceRead(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: IllegalArgumentException) {
    AppResult.Failure(AppError.CorruptedData)
} catch (_: SQLiteException) {
    AppResult.Failure(AppError.LocalStorageFailure)
} catch (_: Exception) {
    AppResult.Failure(AppError.Unknown)
}

private suspend fun <T> withNormalizedExternalId(
    ref: ExternalMediaRef,
    block: suspend (String) -> AppResult<T>
): AppResult<T> = try {
    block(ref.normalizedExternalId())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: IllegalArgumentException) {
    AppResult.Failure(AppError.InvalidInput)
}
