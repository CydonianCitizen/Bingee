package com.cydoniancitizen.bingee.data.library.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BingeeDatabaseMigrationTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        requireNotNull(BingeeDatabase::class.java.canonicalName),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrateOneToTwoPreservesCanonicalRowsReferencesMembershipAndAddedTimes() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO media_entries(" +
                    "local_media_id, media_type, title, original_title, overview, poster_url, " +
                    "release_date, created_at, metadata_updated_at) " +
                    "VALUES(1, 'MOVIE', 'Movie', NULL, 'Movie overview', NULL, '2020-01-01', '2026-08-01T10:00:00Z', '2026-08-01T10:00:00Z')"
            )
            execSQL(
                "INSERT INTO media_entries(" +
                    "local_media_id, media_type, title, original_title, overview, poster_url, " +
                    "release_date, created_at, metadata_updated_at) " +
                    "VALUES(2, 'SERIES', 'Series', NULL, 'Series overview', NULL, '2021-02-02', '2026-08-01T11:00:00Z', '2026-08-01T11:00:00Z')"
            )
            execSQL("INSERT INTO external_refs(local_media_id, source, external_id) VALUES(1, 'TMDB', '101')")
            execSQL("INSERT INTO external_refs(local_media_id, source, external_id) VALUES(2, 'TMDB', '202')")
            execSQL("INSERT INTO library_entries(local_media_id, added_at) VALUES(1, '2026-08-02T10:00:00Z')")
            execSQL("INSERT INTO library_entries(local_media_id, added_at) VALUES(2, '2026-08-02T11:00:00Z')")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        assertEquals(2, migrated.count("media_entries"))
        assertEquals(2, migrated.count("external_refs"))
        assertEquals(2, migrated.count("library_entries"))
        assertEquals(0, migrated.count("media_details"))
        assertEquals(0, migrated.count("media_genres"))
        migrated.query("SELECT title, media_type FROM media_entries ORDER BY local_media_id").use { cursor ->
            assertFalse(cursor.isAfterLast)
            cursor.moveToFirst()
            assertEquals("Movie", cursor.getString(0))
            assertEquals("MOVIE", cursor.getString(1))
            cursor.moveToNext()
            assertEquals("Series", cursor.getString(0))
            assertEquals("SERIES", cursor.getString(1))
        }
        migrated.query("SELECT added_at FROM library_entries ORDER BY local_media_id").use { cursor ->
            cursor.moveToFirst()
            assertEquals("2026-08-02T10:00:00Z", cursor.getString(0))
            cursor.moveToNext()
            assertEquals("2026-08-02T11:00:00Z", cursor.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migrateTwoToThreePreservesVersionTwoDataAndCreatesEmptyProgressStructures() {
        helper.createDatabase(V2_TO_V3_DB, 2).apply {
            insertVersionTwoFixture()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(V2_TO_V3_DB, 3, true, MIGRATION_2_3)

        assertVersionTwoFixtureAndEmptyMilestoneSixTables(migrated)
        migrated.close()
    }

    @Test
    fun migrateOneThroughSixPreservesCanonicalRowsAndValidatesFinalSchema() {
        helper.createDatabase(V1_TO_V6_DB, 1).apply {
            execSQL(
                "INSERT INTO media_entries(local_media_id, media_type, title, original_title, " +
                    "overview, poster_url, release_date, created_at, metadata_updated_at) " +
                    "VALUES(1, 'SERIES', 'Series', NULL, NULL, NULL, '2020-01-01', " +
                    "'2026-08-01T10:00:00Z', '2026-08-01T10:00:00Z')"
            )
            execSQL("INSERT INTO external_refs(local_media_id, source, external_id) VALUES(1, 'TMDB', '202')")
            execSQL("INSERT INTO library_entries(local_media_id, added_at) VALUES(1, '2026-08-02T10:00:00Z')")
            execSQL(
                "INSERT INTO media_entries(local_media_id, media_type, title, original_title, overview, " +
                    "poster_url, release_date, created_at, metadata_updated_at) VALUES" +
                    "(2, 'MOVIE', 'Movie', NULL, NULL, NULL, '2021-01-01', " +
                    "'2026-08-01T10:00:00Z', '2026-08-01T11:00:00Z')"
            )
            execSQL("INSERT INTO external_refs(local_media_id, source, external_id) VALUES(2, 'TMDB', '303')")
            execSQL("INSERT INTO library_entries(local_media_id, added_at) VALUES(2, '2026-08-02T11:00:00Z')")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            V1_TO_V6_DB,
            6,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6
        )

        assertEquals(2, migrated.count("media_entries"))
        assertEquals(2, migrated.count("external_refs"))
        assertEquals(2, migrated.count("library_entries"))
        assertNewMilestoneSixTablesEmpty(migrated)
        assertEquals(0, migrated.count("media_ratings"))
        assertEquals(1, migrated.count("release_events"))
        assertEquals(0, migrated.count("calendar_refresh_state"))
        assertEquals(0, migrated.count("notification_deliveries"))
        migrated.close()
    }

    @Test
    fun migrateFiveToSixPreservesAllVersionFiveDataAndCreatesEmptyDeliveryLedger() {
        helper.createDatabase(V5_TO_V6_DB, 5).apply {
            execSQL(
                "INSERT INTO media_entries(local_media_id, media_type, title, original_title, overview, poster_url, " +
                    "release_date, created_at, metadata_updated_at) VALUES" +
                    "(1, 'MOVIE', 'Movie', NULL, NULL, NULL, '2026-08-05', " +
                    "'2026-08-01T10:00:00Z', '2026-08-03T10:00:00Z')"
            )
            execSQL("INSERT INTO external_refs(local_media_id, source, external_id) VALUES(1, 'TMDB', '42')")
            execSQL("INSERT INTO library_entries(local_media_id, added_at) VALUES(1, '2026-08-02T10:00:00Z')")
            execSQL(
                "INSERT INTO media_ratings(local_media_id, rating_value, rated_at, updated_at) " +
                    "VALUES(1, 8, '2026-08-03T11:00:00Z', '2026-08-03T11:00:00Z')"
            )
            execSQL("INSERT INTO movie_watch_progress(local_media_id, watched_at) VALUES(1, '2026-08-03T11:00:00Z')")
            execSQL(
                "INSERT INTO release_events(local_event_id, local_media_id, local_season_id, local_episode_id, " +
                    "source, subject_type, subject_external_id, event_type, event_date, projected_at, " +
                    "source_metadata_updated_at) VALUES(1, 1, NULL, NULL, 'TMDB', 'MEDIA', '42', " +
                    "'MOVIE_RELEASE', '2026-08-05', '2026-08-03T10:00:00Z', '2026-08-03T10:00:00Z')"
            )
            execSQL(
                "INSERT INTO calendar_refresh_state(singleton_key, last_successful_refresh_at) " +
                    "VALUES(1, '2026-08-03T12:00:00Z')"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(V5_TO_V6_DB, 6, true, MIGRATION_5_6)

        listOf(
            "media_entries",
            "external_refs",
            "library_entries",
            "media_ratings",
            "movie_watch_progress",
            "release_events",
            "calendar_refresh_state"
        ).forEach { assertEquals(1, migrated.count(it)) }
        assertEquals(0, migrated.count("notification_deliveries"))
        migrated.close()
    }

    @Test
    fun migrateSixToSevenCreatesPortablePreferencesWithoutDeviceEnablement() {
        helper.createDatabase(V6_TO_V7_DB, 6).close()

        val migrated = helper.runMigrationsAndValidate(V6_TO_V7_DB, 7, true, MIGRATION_6_7)

        assertEquals(1, migrated.count("portable_preferences"))
        migrated.query(
            "SELECT notification_lead_days, notify_movie_releases, notify_season_premieres, " +
                "notify_episode_airings, legacy_bridge_completed FROM portable_preferences"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals(0, cursor.getInt(4))
        }
        migrated.close()
    }

    @Test
    fun migrateOneThroughSevenPreservesExistingRowsAndValidatesFullChain() {
        helper.createDatabase(V1_TO_V7_DB, 1).apply {
            execSQL(
                "INSERT INTO media_entries(local_media_id, media_type, title, original_title, overview, poster_url, " +
                    "release_date, created_at, metadata_updated_at) VALUES(1, 'MOVIE', 'Movie', NULL, NULL, NULL, " +
                    "'2026-01-01', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')"
            )
            execSQL("INSERT INTO external_refs(local_media_id, source, external_id) VALUES(1, 'TMDB', '1')")
            execSQL("INSERT INTO library_entries(local_media_id, added_at) VALUES(1, '2026-01-02T00:00:00Z')")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            V1_TO_V7_DB,
            7,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7
        )

        assertEquals(1, migrated.count("media_entries"))
        assertEquals(1, migrated.count("external_refs"))
        assertEquals(1, migrated.count("library_entries"))
        assertEquals(1, migrated.count("portable_preferences"))
        migrated.close()
    }

    @Test
    fun migrateFourToFiveBackfillsDatedMetadataAndPreservesPersonalState() {
        helper.createDatabase(V4_TO_V5_DB, 4).apply {
            execSQL(
                "INSERT INTO media_entries(local_media_id, media_type, title, original_title, overview, " +
                    "poster_url, release_date, created_at, metadata_updated_at) VALUES" +
                    "(1, 'MOVIE', 'Movie', NULL, NULL, NULL, '2027-01-02', " +
                    "'2026-08-01T10:00:00Z', '2026-08-03T10:00:00Z')," +
                    "(2, 'SERIES', 'Series', NULL, NULL, NULL, NULL, " +
                    "'2026-08-01T10:00:00Z', '2026-08-03T10:00:00Z')"
            )
            execSQL("INSERT INTO external_refs(local_media_id, source, external_id) VALUES(1, 'TMDB', '42')")
            execSQL("INSERT INTO external_refs(local_media_id, source, external_id) VALUES(2, 'TMDB', '100')")
            execSQL("INSERT INTO library_entries(local_media_id, added_at) VALUES(1, '2026-08-02T10:00:00Z')")
            execSQL("INSERT INTO library_entries(local_media_id, added_at) VALUES(2, '2026-08-02T10:00:00Z')")
            execSQL(
                "INSERT INTO seasons(local_season_id, local_media_id, source, external_id, season_number, " +
                    "name, overview, poster_url, air_date, episode_count, metadata_updated_at, episodes_fetched_at) " +
                    "VALUES(10, 2, 'TMDB', '42', 0, 'Specials', NULL, NULL, '2027-01-03', 2, " +
                    "'2026-08-03T10:00:00Z', '2026-08-03T10:00:00Z')"
            )
            execSQL(
                "INSERT INTO episodes(local_episode_id, local_season_id, source, external_id, episode_number, " +
                    "title, overview, air_date, runtime_minutes, still_url, metadata_updated_at) VALUES" +
                    "(20, 10, 'TMDB', '42', 1, 'Dated', NULL, '2027-01-04', NULL, NULL, " +
                    "'2026-08-03T10:00:00Z')," +
                    "(21, 10, 'TMDB', '43', 2, 'Undated', NULL, NULL, NULL, NULL, " +
                    "'2026-08-03T10:00:00Z')"
            )
            execSQL("INSERT INTO movie_watch_progress(local_media_id, watched_at) VALUES(1, '2026-08-03T11:00:00Z')")
            execSQL(
                "INSERT INTO episode_watch_progress(local_episode_id, watched_at) VALUES(20, '2026-08-03T11:00:00Z')"
            )
            execSQL(
                "INSERT INTO media_ratings(local_media_id, rating_value, rated_at, updated_at) " +
                    "VALUES(1, 8, '2026-08-03T11:00:00Z', '2026-08-03T11:00:00Z')"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(V4_TO_V5_DB, 5, true, MIGRATION_4_5)

        assertEquals(3, migrated.count("release_events"))
        assertEquals(0, migrated.count("calendar_refresh_state"))
        assertEquals(1, migrated.count("media_ratings"))
        assertEquals(1, migrated.count("movie_watch_progress"))
        assertEquals(1, migrated.count("episode_watch_progress"))
        assertEquals(2, migrated.count("library_entries"))
        migrated.query(
            "SELECT subject_type, event_type, event_date FROM release_events " +
                "ORDER BY event_date"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("MEDIA", cursor.getString(0))
            assertEquals("MOVIE_RELEASE", cursor.getString(1))
            assertEquals("2027-01-02", cursor.getString(2))
            cursor.moveToNext()
            assertEquals("SEASON", cursor.getString(0))
            cursor.moveToNext()
            assertEquals("EPISODE", cursor.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migrateThreeToFourPreservesAllVersionThreeDataAndCreatesEmptyRatings() {
        helper.createDatabase(V3_TO_V4_DB, 3).apply {
            execSQL(
                "INSERT INTO media_entries(local_media_id, media_type, title, original_title, overview, " +
                    "poster_url, release_date, created_at, metadata_updated_at) VALUES" +
                    "(1, 'SERIES', 'Series', 'Original', 'Overview', NULL, '2020-01-01', " +
                    "'2026-08-01T10:00:00Z', '2026-08-01T11:00:00Z')"
            )
            execSQL("INSERT INTO external_refs(local_media_id, source, external_id) VALUES(1, 'TMDB', '202')")
            execSQL("INSERT INTO library_entries(local_media_id, added_at) VALUES(1, '2026-08-02T10:00:00Z')")
            execSQL(
                "INSERT INTO media_entries(local_media_id, media_type, title, original_title, overview, " +
                    "poster_url, release_date, created_at, metadata_updated_at) VALUES" +
                    "(2, 'MOVIE', 'Movie', NULL, NULL, NULL, '2021-01-01', " +
                    "'2026-08-01T10:00:00Z', '2026-08-01T11:00:00Z')"
            )
            execSQL("INSERT INTO external_refs(local_media_id, source, external_id) VALUES(2, 'TMDB', '303')")
            execSQL("INSERT INTO library_entries(local_media_id, added_at) VALUES(2, '2026-08-02T11:00:00Z')")
            execSQL(
                "INSERT INTO media_details(local_media_id, backdrop_url, production_status, original_language, " +
                    "runtime_minutes, episode_runtime_minutes, number_of_seasons, number_of_episodes, " +
                    "details_fetched_at) " +
                    "VALUES(1, NULL, 'ENDED', 'en', NULL, 50, 1, 1, '2026-08-03T10:00:00Z')"
            )
            execSQL("INSERT INTO media_genres(local_media_id, genre_order, name) VALUES(1, 0, 'Drama')")
            execSQL(
                "INSERT INTO seasons(local_season_id, local_media_id, source, external_id, season_number, name, " +
                    "overview, poster_url, air_date, episode_count, metadata_updated_at, episodes_fetched_at) " +
                    "VALUES(11, 1, 'TMDB', '11', 1, 'Season 1', NULL, NULL, NULL, 1, " +
                    "'2026-08-03T10:00:00Z', '2026-08-03T10:00:00Z')"
            )
            execSQL(
                "INSERT INTO episodes(local_episode_id, local_season_id, source, external_id, episode_number, title, " +
                    "overview, air_date, runtime_minutes, still_url, metadata_updated_at) " +
                    "VALUES(101, 11, 'TMDB', '101', 1, 'Episode', NULL, NULL, 50, NULL, '2026-08-03T10:00:00Z')"
            )
            execSQL(
                "INSERT INTO episode_watch_progress(local_episode_id, watched_at) VALUES(101, '2026-08-03T11:00:00Z')"
            )
            execSQL("INSERT INTO movie_watch_progress(local_media_id, watched_at) VALUES(2, '2026-08-03T11:00:00Z')")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(V3_TO_V4_DB, 4, true, MIGRATION_3_4)

        listOf("media_entries", "external_refs", "library_entries").forEach {
            assertEquals(2, migrated.count(it))
        }
        listOf(
            "media_details",
            "media_genres",
            "seasons",
            "episodes",
            "episode_watch_progress"
        ).forEach { assertEquals(1, migrated.count(it)) }
        assertEquals(1, migrated.count("movie_watch_progress"))
        assertEquals(0, migrated.count("media_ratings"))
        migrated.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertVersionTwoFixture() {
        execSQL(
            "INSERT INTO media_entries(local_media_id, media_type, title, original_title, " +
                "overview, poster_url, release_date, created_at, metadata_updated_at) " +
                "VALUES(1, 'SERIES', 'Series', NULL, 'Overview', NULL, '2020-01-01', " +
                "'2026-08-01T10:00:00Z', '2026-08-01T11:00:00Z')"
        )
        execSQL("INSERT INTO external_refs(local_media_id, source, external_id) VALUES(1, 'TMDB', '202')")
        execSQL("INSERT INTO library_entries(local_media_id, added_at) VALUES(1, '2026-08-02T10:00:00Z')")
        execSQL(
            "INSERT INTO media_details(local_media_id, backdrop_url, production_status, " +
                "original_language, runtime_minutes, episode_runtime_minutes, number_of_seasons, " +
                "number_of_episodes, details_fetched_at) VALUES(1, NULL, 'ENDED', 'en', NULL, 50, 2, 10, " +
                "'2026-08-03T10:00:00Z')"
        )
        execSQL("INSERT INTO media_genres(local_media_id, genre_order, name) VALUES(1, 0, 'Drama')")
        execSQL("INSERT INTO media_genres(local_media_id, genre_order, name) VALUES(1, 1, 'Mystery')")
    }

    private fun assertVersionTwoFixtureAndEmptyMilestoneSixTables(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        assertEquals(1, database.count("media_entries"))
        assertEquals(1, database.count("external_refs"))
        assertEquals(1, database.count("library_entries"))
        assertEquals(1, database.count("media_details"))
        assertEquals(2, database.count("media_genres"))
        database.query("SELECT name FROM media_genres ORDER BY genre_order").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Drama", cursor.getString(0))
            cursor.moveToNext()
            assertEquals("Mystery", cursor.getString(0))
        }
        assertNewMilestoneSixTablesEmpty(database)
    }

    private fun assertNewMilestoneSixTablesEmpty(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        assertEquals(0, database.count("seasons"))
        assertEquals(0, database.count("episodes"))
        assertEquals(0, database.count("episode_watch_progress"))
        assertEquals(0, database.count("movie_watch_progress"))
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DB = "bingee-migration-1-2"
        const val V2_TO_V3_DB = "bingee-migration-2-3"
        const val V1_TO_V6_DB = "bingee-migration-1-6"
        const val V3_TO_V4_DB = "bingee-migration-3-4"
        const val V4_TO_V5_DB = "bingee-migration-4-5"
        const val V5_TO_V6_DB = "bingee-migration-5-6"
        const val V6_TO_V7_DB = "bingee-migration-6-7"
        const val V1_TO_V7_DB = "bingee-migration-1-7"
    }
}
