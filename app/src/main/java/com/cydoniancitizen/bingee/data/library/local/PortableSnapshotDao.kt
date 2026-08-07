package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

internal data class PortableSnapshotRows(
    val media: List<MediaEntity>,
    val refs: List<ExternalRefEntity>,
    val memberships: List<LibraryMembershipEntity>,
    val details: List<MediaDetailsEntity>,
    val genres: List<MediaGenreEntity>,
    val seasons: List<SeasonEntity>,
    val episodes: List<EpisodeEntity>,
    val episodeProgress: List<EpisodeWatchProgressEntity>,
    val movieProgress: List<MovieWatchProgressEntity>,
    val seriesProgress: List<SeriesWatchProgressEntity>,
    val ratings: List<MediaRatingEntity>,
    val preferences: PortablePreferencesEntity?
)

@Dao
internal abstract class PortableSnapshotDao {
    @Query("SELECT * FROM media_entries ORDER BY local_media_id")
    protected abstract suspend fun getMedia(): List<MediaEntity>

    @Query("SELECT * FROM external_refs ORDER BY source, external_id")
    protected abstract suspend fun getRefs(): List<ExternalRefEntity>

    @Query("SELECT * FROM library_entries ORDER BY local_media_id")
    protected abstract suspend fun getMemberships(): List<LibraryMembershipEntity>

    @Query("SELECT * FROM media_details ORDER BY local_media_id")
    protected abstract suspend fun getDetails(): List<MediaDetailsEntity>

    @Query("SELECT * FROM media_genres ORDER BY local_media_id, genre_order")
    protected abstract suspend fun getGenres(): List<MediaGenreEntity>

    @Query("SELECT * FROM seasons ORDER BY local_season_id")
    protected abstract suspend fun getSeasons(): List<SeasonEntity>

    @Query("SELECT * FROM episodes ORDER BY local_episode_id")
    protected abstract suspend fun getEpisodes(): List<EpisodeEntity>

    @Query("SELECT * FROM episode_watch_progress ORDER BY local_episode_id")
    protected abstract suspend fun getEpisodeProgress(): List<EpisodeWatchProgressEntity>

    @Query("SELECT * FROM movie_watch_progress ORDER BY local_media_id")
    protected abstract suspend fun getMovieProgress(): List<MovieWatchProgressEntity>

    @Query("SELECT * FROM series_watch_progress ORDER BY local_media_id")
    protected abstract suspend fun getSeriesProgress(): List<SeriesWatchProgressEntity>

    @Query("SELECT * FROM media_ratings ORDER BY local_media_id")
    protected abstract suspend fun getRatings(): List<MediaRatingEntity>

    @Query("SELECT * FROM portable_preferences WHERE singleton_key = 1 LIMIT 1")
    abstract suspend fun getPreferences(): PortablePreferencesEntity?

    @Query("SELECT * FROM portable_preferences WHERE singleton_key = 1 LIMIT 1")
    abstract fun observePreferences(): Flow<PortablePreferencesEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMedia(media: MediaEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertExternalRef(ref: ExternalRefEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMembership(membership: LibraryMembershipEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSeason(season: SeasonEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertEpisode(episode: EpisodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertEpisodeProgress(progress: EpisodeWatchProgressEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMovieProgress(progress: MovieWatchProgressEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSeriesProgress(progress: SeriesWatchProgressEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRating(rating: MediaRatingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun replacePreferences(preferences: PortablePreferencesEntity)

    @Query("DELETE FROM notification_deliveries")
    abstract suspend fun deleteNotificationDeliveries()

    @Query("DELETE FROM release_events")
    abstract suspend fun deleteReleaseEvents()

    @Query("DELETE FROM episode_watch_progress")
    abstract suspend fun deleteEpisodeProgress()

    @Query("DELETE FROM movie_watch_progress")
    abstract suspend fun deleteMovieProgress()

    @Query("DELETE FROM series_watch_progress")
    abstract suspend fun deleteSeriesProgress()

    @Query("DELETE FROM media_ratings")
    abstract suspend fun deleteRatings()

    @Query("DELETE FROM library_entries")
    abstract suspend fun deleteMemberships()

    @Query("DELETE FROM episodes")
    abstract suspend fun deleteEpisodes()

    @Query("DELETE FROM seasons")
    abstract suspend fun deleteSeasons()

    @Query("DELETE FROM media_genres")
    abstract suspend fun deleteGenres()

    @Query("DELETE FROM media_details")
    abstract suspend fun deleteDetails()

    @Query("DELETE FROM external_refs")
    abstract suspend fun deleteRefs()

    @Query("DELETE FROM media_entries")
    abstract suspend fun deleteMedia()

    @Query("DELETE FROM calendar_refresh_state")
    abstract suspend fun deleteCalendarRefreshState()

    @Query("DELETE FROM portable_preferences")
    abstract suspend fun deletePreferences()

    @Transaction
    open suspend fun readSnapshot(): PortableSnapshotRows = PortableSnapshotRows(
        media = getMedia(), refs = getRefs(), memberships = getMemberships(),
        details = getDetails(), genres = getGenres(), seasons = getSeasons(),
        episodes = getEpisodes(), episodeProgress = getEpisodeProgress(),
        movieProgress = getMovieProgress(), seriesProgress = getSeriesProgress(),
        ratings = getRatings(),
        preferences = getPreferences()
    )
}
