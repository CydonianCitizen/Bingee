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

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DB = "bingee-migration-1-2"
    }
}
