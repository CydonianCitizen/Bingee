package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import com.cydoniancitizen.bingee.data.calendar.ProjectedReleaseEvent
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class ReleaseEventDao {
    @Query(
        """
        SELECT release_events.source,
               external_refs.external_id AS parent_external_id,
               release_events.subject_type,
               release_events.subject_external_id,
               release_events.event_type,
               release_events.event_date,
               media_entries.media_type,
               media_entries.title AS media_title,
               media_entries.poster_url,
               seasons.season_number,
               episodes.episode_number,
               CASE
                   WHEN release_events.event_type = 'EPISODE_AIRING' THEN episodes.title
                   WHEN release_events.event_type = 'SEASON_PREMIERE' THEN seasons.name
                   ELSE NULL
               END AS subject_title
        FROM release_events
        INNER JOIN media_entries USING(local_media_id)
        INNER JOIN library_entries USING(local_media_id)
        INNER JOIN external_refs
            ON external_refs.local_media_id = release_events.local_media_id
           AND external_refs.source = release_events.source
        LEFT JOIN seasons ON seasons.local_season_id = release_events.local_season_id
        LEFT JOIN episodes ON episodes.local_episode_id = release_events.local_episode_id
        WHERE release_events.event_date >= :fromDate
          AND (:throughDate IS NULL OR release_events.event_date <= :throughDate)
        ORDER BY release_events.event_date ASC,
                 CASE release_events.event_type
                     WHEN 'EPISODE_AIRING' THEN 0
                     WHEN 'SEASON_PREMIERE' THEN 1
                     ELSE 2
                 END ASC,
                 LOWER(media_entries.title) ASC,
                 release_events.source ASC,
                 release_events.subject_type ASC,
                 release_events.subject_external_id ASC,
                 release_events.event_type ASC
        """
    )
    abstract fun observeActiveEvents(fromDate: LocalDate, throughDate: LocalDate? = null): Flow<List<ReleaseEventRow>>

    @Query(
        """
        SELECT release_events.source,
               external_refs.external_id AS parent_external_id,
               release_events.subject_type,
               release_events.subject_external_id,
               release_events.event_type,
               release_events.event_date,
               media_entries.media_type,
               media_entries.title AS media_title,
               media_entries.poster_url,
               seasons.season_number,
               episodes.episode_number,
               CASE
                   WHEN release_events.event_type = 'EPISODE_AIRING' THEN episodes.title
                   WHEN release_events.event_type = 'SEASON_PREMIERE' THEN seasons.name
                   ELSE NULL
               END AS subject_title
        FROM release_events
        INNER JOIN media_entries USING(local_media_id)
        INNER JOIN library_entries USING(local_media_id)
        INNER JOIN external_refs
            ON external_refs.local_media_id = release_events.local_media_id
           AND external_refs.source = release_events.source
        LEFT JOIN seasons ON seasons.local_season_id = release_events.local_season_id
        LEFT JOIN episodes ON episodes.local_episode_id = release_events.local_episode_id
        WHERE release_events.event_date BETWEEN :fromDate AND :throughDate
          AND NOT EXISTS (
              SELECT 1
              FROM series_state_overrides
              WHERE series_state_overrides.local_media_id = release_events.local_media_id
                AND series_state_overrides.is_abandoned = 1
          )
        ORDER BY release_events.event_date ASC,
                 CASE release_events.event_type
                     WHEN 'EPISODE_AIRING' THEN 0
                     WHEN 'SEASON_PREMIERE' THEN 1
                     ELSE 2
                 END ASC,
                 LOWER(media_entries.title) ASC,
                 release_events.source ASC,
                 release_events.subject_type ASC,
                 release_events.subject_external_id ASC,
                 release_events.event_type ASC
        """
    )
    abstract suspend fun getActiveEventsBetween(fromDate: LocalDate, throughDate: LocalDate): List<ReleaseEventRow>

    @Query(
        "SELECT last_successful_refresh_at FROM calendar_refresh_state " +
            "WHERE singleton_key = 1 LIMIT 1"
    )
    abstract fun observeLastSuccessfulRefresh(): Flow<Instant?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun replaceRefreshState(state: CalendarRefreshStateEntity)

    @Query(
        """
        SELECT media_entries.local_media_id, NULL AS local_season_id, NULL AS local_episode_id
        FROM media_entries
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source AND external_refs.external_id = :externalId
        LIMIT 1
        """
    )
    protected abstract suspend fun getMediaIds(source: MediaSource, externalId: String): ReleaseSubjectLocalIds?

    @Query(
        """
        SELECT seasons.local_media_id, seasons.local_season_id, NULL AS local_episode_id
        FROM seasons
        WHERE seasons.source = :source AND seasons.external_id = :externalId
        LIMIT 1
        """
    )
    protected abstract suspend fun getSeasonIds(source: MediaSource, externalId: String): ReleaseSubjectLocalIds?

    @Query(
        """
        SELECT seasons.local_media_id, episodes.local_season_id, episodes.local_episode_id
        FROM episodes
        INNER JOIN seasons USING(local_season_id)
        WHERE episodes.source = :source AND episodes.external_id = :externalId
        LIMIT 1
        """
    )
    protected abstract suspend fun getEpisodeIds(source: MediaSource, externalId: String): ReleaseSubjectLocalIds?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertEvent(event: ReleaseEventEntity)

    @Query(
        """
        UPDATE release_events
        SET local_media_id = :localMediaId,
            local_season_id = :localSeasonId,
            local_episode_id = :localEpisodeId,
            event_date = :eventDate,
            projected_at = :projectedAt,
            source_metadata_updated_at = :sourceMetadataUpdatedAt
        WHERE source = :source
          AND subject_type = :subjectType
          AND subject_external_id = :subjectExternalId
          AND event_type = :eventType
        """
    )
    protected abstract suspend fun updateEvent(
        source: MediaSource,
        subjectType: ReleaseSubjectType,
        subjectExternalId: String,
        eventType: ReleaseEventType,
        localMediaId: Long,
        localSeasonId: Long?,
        localEpisodeId: Long?,
        eventDate: LocalDate,
        projectedAt: Instant,
        sourceMetadataUpdatedAt: Instant
    ): Int

    @Query(
        """
        DELETE FROM release_events
        WHERE source = :source
          AND subject_type = :subjectType
          AND subject_external_id = :subjectExternalId
          AND event_type = :eventType
        """
    )
    protected abstract suspend fun deleteEvent(
        source: MediaSource,
        subjectType: ReleaseSubjectType,
        subjectExternalId: String,
        eventType: ReleaseEventType
    ): Int

    @Transaction
    open suspend fun reconcileMovie(parentRef: ExternalMediaRef, event: ProjectedReleaseEvent?) {
        replaceProjection(
            source = parentRef.source,
            subjectType = ReleaseSubjectType.MEDIA,
            subjectExternalId = parentRef.externalId,
            eventType = ReleaseEventType.MOVIE_RELEASE,
            event = event
        )
    }

    @Transaction
    open suspend fun reconcileSeason(seasonRef: ExternalMediaRef, event: ProjectedReleaseEvent?) {
        replaceProjection(
            source = seasonRef.source,
            subjectType = ReleaseSubjectType.SEASON,
            subjectExternalId = seasonRef.externalId,
            eventType = ReleaseEventType.SEASON_PREMIERE,
            event = event
        )
    }

    @Transaction
    open suspend fun reconcileSeason(season: SeasonEntity, event: ProjectedReleaseEvent?) {
        replaceProjection(
            source = season.source,
            subjectType = ReleaseSubjectType.SEASON,
            subjectExternalId = season.externalId,
            eventType = ReleaseEventType.SEASON_PREMIERE,
            event = event,
            ids = ReleaseSubjectLocalIds(season.localMediaId, season.localSeasonId, null)
        )
    }

    @Transaction
    open suspend fun reconcileEpisode(episodeRef: ExternalMediaRef, event: ProjectedReleaseEvent?) {
        replaceProjection(
            source = episodeRef.source,
            subjectType = ReleaseSubjectType.EPISODE,
            subjectExternalId = episodeRef.externalId,
            eventType = ReleaseEventType.EPISODE_AIRING,
            event = event
        )
    }

    @Transaction
    open suspend fun reconcileEpisode(episode: EpisodeEntity, localMediaId: Long, event: ProjectedReleaseEvent?) {
        replaceProjection(
            source = episode.source,
            subjectType = ReleaseSubjectType.EPISODE,
            subjectExternalId = episode.externalId,
            eventType = ReleaseEventType.EPISODE_AIRING,
            event = event,
            ids = ReleaseSubjectLocalIds(localMediaId, episode.localSeasonId, episode.localEpisodeId)
        )
    }

    private suspend fun replaceProjection(
        source: MediaSource,
        subjectType: ReleaseSubjectType,
        subjectExternalId: String,
        eventType: ReleaseEventType,
        event: ProjectedReleaseEvent?,
        ids: ReleaseSubjectLocalIds? = null
    ) {
        require(subjectExternalId.isNotBlank())
        if (event == null) {
            deleteEvent(source, subjectType, subjectExternalId, eventType)
            return
        }
        check(event.identity.source == source)
        check(event.identity.subjectType == subjectType)
        check(event.identity.externalId == subjectExternalId)
        check(event.identity.eventType == eventType)
        val localIds = ids ?: when (subjectType) {
            ReleaseSubjectType.MEDIA -> getMediaIds(source, subjectExternalId)
            ReleaseSubjectType.SEASON -> getSeasonIds(source, subjectExternalId)
            ReleaseSubjectType.EPISODE -> getEpisodeIds(source, subjectExternalId)
        } ?: error("Release event source metadata is missing")
        val updated = updateEvent(
            source = source,
            subjectType = subjectType,
            subjectExternalId = subjectExternalId,
            eventType = eventType,
            localMediaId = localIds.localMediaId,
            localSeasonId = localIds.localSeasonId,
            localEpisodeId = localIds.localEpisodeId,
            eventDate = event.eventDate,
            projectedAt = event.projectedAt,
            sourceMetadataUpdatedAt = event.sourceMetadataUpdatedAt
        )
        if (updated == 0) {
            insertEvent(
                ReleaseEventEntity(
                    localMediaId = localIds.localMediaId,
                    localSeasonId = localIds.localSeasonId,
                    localEpisodeId = localIds.localEpisodeId,
                    source = source,
                    subjectType = subjectType,
                    subjectExternalId = subjectExternalId,
                    eventType = eventType,
                    eventDate = event.eventDate,
                    projectedAt = event.projectedAt,
                    sourceMetadataUpdatedAt = event.sourceMetadataUpdatedAt
                )
            )
        }
    }

    @Transaction
    open suspend fun backfill(projectedAt: Instant) {
        deleteMoviesWithoutDates()
        updateMovieDates(projectedAt)
        insertMovieDates(projectedAt)
        deleteSeasonsWithoutDates()
        updateSeasonDates(projectedAt)
        insertSeasonDates(projectedAt)
        deleteEpisodesWithoutDates()
        updateEpisodeDates(projectedAt)
        insertEpisodeDates(projectedAt)
    }

    @Query(
        """
        DELETE FROM release_events
        WHERE subject_type = 'MEDIA' AND event_type = 'MOVIE_RELEASE'
          AND EXISTS (
              SELECT 1 FROM media_entries
              INNER JOIN external_refs USING(local_media_id)
              WHERE external_refs.source = release_events.source
                AND external_refs.external_id = release_events.subject_external_id
                AND media_entries.release_date IS NULL
          )
        """
    )
    protected abstract suspend fun deleteMoviesWithoutDates()

    @Query(
        """
        UPDATE release_events
        SET event_date = (
                SELECT media_entries.release_date FROM media_entries
                INNER JOIN external_refs USING(local_media_id)
                WHERE external_refs.source = release_events.source
                  AND external_refs.external_id = release_events.subject_external_id
            ),
            projected_at = :projectedAt,
            source_metadata_updated_at = (
                SELECT media_entries.metadata_updated_at FROM media_entries
                INNER JOIN external_refs USING(local_media_id)
                WHERE external_refs.source = release_events.source
                  AND external_refs.external_id = release_events.subject_external_id
            )
        WHERE subject_type = 'MEDIA' AND event_type = 'MOVIE_RELEASE'
          AND event_date != (
              SELECT media_entries.release_date FROM media_entries
              INNER JOIN external_refs USING(local_media_id)
              WHERE external_refs.source = release_events.source
                AND external_refs.external_id = release_events.subject_external_id
          )
        """
    )
    protected abstract suspend fun updateMovieDates(projectedAt: Instant)

    @Query(
        """
        INSERT OR IGNORE INTO release_events(
            local_event_id, local_media_id, local_season_id, local_episode_id, source,
            subject_type, subject_external_id, event_type, event_date, projected_at,
            source_metadata_updated_at
        )
        SELECT NULL, media_entries.local_media_id, NULL, NULL, external_refs.source,
               'MEDIA', external_refs.external_id, 'MOVIE_RELEASE', media_entries.release_date,
               :projectedAt, media_entries.metadata_updated_at
        FROM media_entries
        INNER JOIN external_refs USING(local_media_id)
        WHERE media_entries.media_type = 'MOVIE' AND media_entries.release_date IS NOT NULL
        """
    )
    protected abstract suspend fun insertMovieDates(projectedAt: Instant)

    @Query(
        """
        DELETE FROM release_events
        WHERE subject_type = 'SEASON' AND event_type = 'SEASON_PREMIERE'
          AND EXISTS (
              SELECT 1 FROM seasons
              WHERE seasons.source = release_events.source
                AND seasons.external_id = release_events.subject_external_id
                AND seasons.air_date IS NULL
          )
        """
    )
    protected abstract suspend fun deleteSeasonsWithoutDates()

    @Query(
        """
        UPDATE release_events
        SET event_date = (
                SELECT seasons.air_date FROM seasons
                WHERE seasons.source = release_events.source
                  AND seasons.external_id = release_events.subject_external_id
            ),
            projected_at = :projectedAt,
            source_metadata_updated_at = (
                SELECT seasons.metadata_updated_at FROM seasons
                WHERE seasons.source = release_events.source
                  AND seasons.external_id = release_events.subject_external_id
            )
        WHERE subject_type = 'SEASON' AND event_type = 'SEASON_PREMIERE'
          AND event_date != (
              SELECT seasons.air_date FROM seasons
              WHERE seasons.source = release_events.source
                AND seasons.external_id = release_events.subject_external_id
          )
        """
    )
    protected abstract suspend fun updateSeasonDates(projectedAt: Instant)

    @Query(
        """
        INSERT OR IGNORE INTO release_events(
            local_event_id, local_media_id, local_season_id, local_episode_id, source,
            subject_type, subject_external_id, event_type, event_date, projected_at,
            source_metadata_updated_at
        )
        SELECT NULL, seasons.local_media_id, seasons.local_season_id, NULL, seasons.source,
               'SEASON', seasons.external_id, 'SEASON_PREMIERE', seasons.air_date,
               :projectedAt, seasons.metadata_updated_at
        FROM seasons
        WHERE seasons.air_date IS NOT NULL
        """
    )
    protected abstract suspend fun insertSeasonDates(projectedAt: Instant)

    @Query(
        """
        DELETE FROM release_events
        WHERE subject_type = 'EPISODE' AND event_type = 'EPISODE_AIRING'
          AND EXISTS (
              SELECT 1 FROM episodes
              WHERE episodes.source = release_events.source
                AND episodes.external_id = release_events.subject_external_id
                AND episodes.air_date IS NULL
          )
        """
    )
    protected abstract suspend fun deleteEpisodesWithoutDates()

    @Query(
        """
        UPDATE release_events
        SET event_date = (
                SELECT episodes.air_date FROM episodes
                WHERE episodes.source = release_events.source
                  AND episodes.external_id = release_events.subject_external_id
            ),
            projected_at = :projectedAt,
            source_metadata_updated_at = (
                SELECT episodes.metadata_updated_at FROM episodes
                WHERE episodes.source = release_events.source
                  AND episodes.external_id = release_events.subject_external_id
            )
        WHERE subject_type = 'EPISODE' AND event_type = 'EPISODE_AIRING'
          AND event_date != (
              SELECT episodes.air_date FROM episodes
              WHERE episodes.source = release_events.source
                AND episodes.external_id = release_events.subject_external_id
          )
        """
    )
    protected abstract suspend fun updateEpisodeDates(projectedAt: Instant)

    @Query(
        """
        INSERT OR IGNORE INTO release_events(
            local_event_id, local_media_id, local_season_id, local_episode_id, source,
            subject_type, subject_external_id, event_type, event_date, projected_at,
            source_metadata_updated_at
        )
        SELECT NULL, seasons.local_media_id, episodes.local_season_id, episodes.local_episode_id,
               episodes.source, 'EPISODE', episodes.external_id, 'EPISODE_AIRING',
               episodes.air_date, :projectedAt, episodes.metadata_updated_at
        FROM episodes
        INNER JOIN seasons USING(local_season_id)
        WHERE episodes.air_date IS NOT NULL
        """
    )
    protected abstract suspend fun insertEpisodeDates(projectedAt: Instant)
}
