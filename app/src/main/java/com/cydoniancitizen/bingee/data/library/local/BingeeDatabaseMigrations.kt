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
