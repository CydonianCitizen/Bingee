package com.cydoniancitizen.bingee.domain.calendar

import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.model.BackgroundRefreshTarget
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedSeason
import com.cydoniancitizen.bingee.core.model.CalendarRefreshOutcome
import com.cydoniancitizen.bingee.core.model.CalendarRefreshSummary
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseCalendarWindow
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.CalendarRefreshCoordinator
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.MediaDetailsRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import com.cydoniancitizen.bingee.domain.repository.SeriesRepository
import com.cydoniancitizen.bingee.domain.repository.TmdbCredentialRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Singleton
internal class DefaultCalendarRefreshCoordinator @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val detailsRepository: MediaDetailsRepository,
    private val seriesRepository: SeriesRepository,
    private val calendarRepository: ReleaseCalendarRepository,
    private val credentialRepository: TmdbCredentialRepository,
    private val clock: Clock,
    private val dateSource: CalendarDateSource,
    private val window: ReleaseCalendarWindow
) : CalendarRefreshCoordinator {
    override suspend fun refresh(): CalendarRefreshSummary {
        val entries = when (val result = libraryRepository.observeEntries().first()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return failureSummary(result.error)
        }.distinctBy { it.mediaRef to it.mediaType }

        return refresh(entries.map { BackgroundRefreshTarget(it.mediaRef, it.mediaType) })
    }

    override suspend fun refresh(targets: List<BackgroundRefreshTarget>): CalendarRefreshSummary {
        val entries = targets.distinctBy { it.mediaRef to it.mediaType }.take(MAX_REFRESH_TARGETS)
        if (entries.isEmpty()) return noWorkSummary()
        val tmdbAvailable = credentialRepository.status.value.canRefresh()
        val semaphore = Semaphore(REMOTE_CONCURRENCY_LIMIT)
        val counts = supervisorScope {
            entries.map { entry ->
                async {
                    semaphore.withPermit {
                        try {
                            refreshEntry(entry, tmdbAvailable)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            RefreshCounts(failed = 1, error = AppError.Unknown)
                        }
                    }
                }
            }.awaitAll().fold(RefreshCounts(), RefreshCounts::plus)
        }

        val finalized = if (counts.succeeded > 0) finalizeSuccessfulWrites(counts) else counts
        return finalized.toSummary(entries.size)
    }

    private suspend fun refreshEntry(entry: BackgroundRefreshTarget, tmdbAvailable: Boolean): RefreshCounts {
        val tmdbId = entry.mediaRef.takeIf { it.source == MediaSource.TMDB }
            ?.externalId?.toLongOrNull()?.takeIf { it > 0 } ?: return RefreshCounts(skipped = 1)
        if (!tmdbAvailable) return RefreshCounts(skipped = 1, error = AppError.Unauthorized)
        if (entry.mediaType == MediaType.MOVIE) {
            return detailsRepository.refreshDetails(tmdbId, MediaType.MOVIE, force = true).toCounts()
        }

        val before = readSeasons(tmdbId)
        val details = detailsRepository.refreshDetails(tmdbId, MediaType.SERIES, force = true)
        var counts = details.toCounts()
        val after = if (details is AppResult.Success) readSeasons(tmdbId) else before
        val selected = selectRelevantSeasonNumbers(
            beforeRefresh = before,
            afterRefresh = after,
            today = dateSource.currentDate(),
            window = window
        )
        selected.forEach { seasonNumber ->
            counts += seriesRepository.refreshSeason(tmdbId, seasonNumber, force = true).toCounts()
        }
        return counts
    }

    private suspend fun readSeasons(tmdbId: Long): List<CachedSeason> =
        when (val result = seriesRepository.observeSeasons(tmdbId).first()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> emptyList()
        }

    private suspend fun finalizeSuccessfulWrites(counts: RefreshCounts): RefreshCounts {
        val backfill = calendarRepository.backfill()
        if (backfill is AppResult.Failure) return counts + RefreshCounts(failed = 1, error = backfill.error)
        val state = calendarRepository.markRefreshSuccessful(clock.instant())
        return if (state is AppResult.Failure) {
            counts + RefreshCounts(failed = 1, error = state.error)
        } else {
            counts
        }
    }

    private fun failureSummary(error: AppError) = CalendarRefreshSummary(
        outcome = CalendarRefreshOutcome.COMPLETE_FAILURE,
        titlesConsidered = 0,
        operationsSucceeded = 0,
        operationsFailed = 1,
        operationsSkipped = 0,
        representativeError = error
    )

    private fun noWorkSummary() = CalendarRefreshSummary(
        outcome = CalendarRefreshOutcome.NO_WORK,
        titlesConsidered = 0,
        operationsSucceeded = 0,
        operationsFailed = 0,
        operationsSkipped = 0
    )

    companion object {
        const val REMOTE_CONCURRENCY_LIMIT = 3

        // ponytail: manual refresh capped at 20 titles; page only if larger libraries need it.
        const val MAX_REFRESH_TARGETS = 20
    }
}

internal fun selectRelevantSeasonNumbers(
    beforeRefresh: List<CachedSeason>,
    afterRefresh: List<CachedSeason>,
    today: LocalDate,
    window: ReleaseCalendarWindow
): List<Int> {
    val seasons = (afterRefresh + beforeRefresh).distinctBy { it.season.seasonNumber }
    val recentStart = window.startDate(today)
    fun CachedSeason.hasUpcomingEpisode() = episodes.any { episode ->
        episode.episode.airDate?.isBefore(today) != true
    }
    fun CachedSeason.isRelevant() = episodesFetchedAt == null ||
        episodeCacheFreshness == CacheFreshness.STALE ||
        season.airDate?.isBefore(recentStart) == false ||
        hasUpcomingEpisode()

    return seasons
        .filter { it.isRelevant() }
        .map { it.season.seasonNumber }
        .distinct()
        .sorted()
}

private fun TmdbCredentialStatus.canRefresh(): Boolean = when (this) {
    TmdbCredentialStatus.Valid -> true
    is TmdbCredentialStatus.TemporarilyUnverifiable -> hasStoredCredential
    else -> false
}

private data class RefreshCounts(
    val succeeded: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val error: AppError? = null
) {
    operator fun plus(other: RefreshCounts) = RefreshCounts(
        succeeded = succeeded + other.succeeded,
        failed = failed + other.failed,
        skipped = skipped + other.skipped,
        error = error ?: other.error
    )

    fun toSummary(titles: Int): CalendarRefreshSummary = CalendarRefreshSummary(
        outcome = when {
            succeeded > 0 && failed == 0 && skipped == 0 -> CalendarRefreshOutcome.COMPLETE_SUCCESS
            succeeded > 0 -> CalendarRefreshOutcome.PARTIAL_SUCCESS
            error == AppError.Unauthorized -> CalendarRefreshOutcome.CREDENTIAL_REQUIRED
            failed > 0 -> CalendarRefreshOutcome.COMPLETE_FAILURE
            else -> CalendarRefreshOutcome.NO_WORK
        },
        titlesConsidered = titles,
        operationsSucceeded = succeeded,
        operationsFailed = failed,
        operationsSkipped = skipped,
        representativeError = error
    )
}

private fun AppResult<Unit>.toCounts(): RefreshCounts = when (this) {
    is AppResult.Success -> RefreshCounts(succeeded = 1)
    is AppResult.Failure -> RefreshCounts(failed = 1, error = error)
}
