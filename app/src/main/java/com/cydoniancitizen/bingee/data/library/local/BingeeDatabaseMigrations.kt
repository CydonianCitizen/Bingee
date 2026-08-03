package com.cydoniancitizen.bingee.data.library.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `media_details` (
                `local_media_id` INTEGER NOT NULL,
                `backdrop_url` TEXT,
                `production_status` TEXT NOT NULL,
                `original_language` TEXT,
                `runtime_minutes` INTEGER,
                `episode_runtime_minutes` INTEGER,
                `number_of_seasons` INTEGER,
                `number_of_episodes` INTEGER,
                `details_fetched_at` TEXT NOT NULL,
                PRIMARY KEY(`local_media_id`),
                FOREIGN KEY(`local_media_id`) REFERENCES `media_entries`(`local_media_id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `media_genres` (
                `local_media_id` INTEGER NOT NULL,
                `genre_order` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                PRIMARY KEY(`local_media_id`, `genre_order`),
                FOREIGN KEY(`local_media_id`) REFERENCES `media_entries`(`local_media_id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS seasons (
                local_season_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                local_media_id INTEGER NOT NULL,
                source TEXT NOT NULL,
                external_id TEXT NOT NULL,
                season_number INTEGER NOT NULL,
                name TEXT,
                overview TEXT,
                poster_url TEXT,
                air_date TEXT,
                episode_count INTEGER NOT NULL,
                metadata_updated_at TEXT NOT NULL,
                episodes_fetched_at TEXT,
                FOREIGN KEY(local_media_id) REFERENCES media_entries(local_media_id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_seasons_local_media_id ON seasons (local_media_id)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_seasons_source_external_id " +
                "ON seasons (source, external_id)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_seasons_local_media_id_season_number " +
                "ON seasons (local_media_id, season_number)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS episodes (
                local_episode_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                local_season_id INTEGER NOT NULL,
                source TEXT NOT NULL,
                external_id TEXT NOT NULL,
                episode_number INTEGER NOT NULL,
                title TEXT NOT NULL,
                overview TEXT,
                air_date TEXT,
                runtime_minutes INTEGER,
                still_url TEXT,
                metadata_updated_at TEXT NOT NULL,
                FOREIGN KEY(local_season_id) REFERENCES seasons(local_season_id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_episodes_local_season_id ON episodes (local_season_id)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_episodes_source_external_id " +
                "ON episodes (source, external_id)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_episodes_local_season_id_episode_number " +
                "ON episodes (local_season_id, episode_number)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS episode_watch_progress (
                local_episode_id INTEGER NOT NULL,
                watched_at TEXT NOT NULL,
                PRIMARY KEY(local_episode_id),
                FOREIGN KEY(local_episode_id) REFERENCES episodes(local_episode_id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS movie_watch_progress (
                local_media_id INTEGER NOT NULL,
                watched_at TEXT NOT NULL,
                PRIMARY KEY(local_media_id),
                FOREIGN KEY(local_media_id) REFERENCES media_entries(local_media_id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS media_ratings (
                local_media_id INTEGER NOT NULL,
                rating_value INTEGER NOT NULL,
                rated_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                PRIMARY KEY(local_media_id),
                FOREIGN KEY(local_media_id) REFERENCES media_entries(local_media_id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}
