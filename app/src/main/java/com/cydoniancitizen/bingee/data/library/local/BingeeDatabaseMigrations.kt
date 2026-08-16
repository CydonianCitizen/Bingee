package com.cydoniancitizen.bingee.data.library.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `series_state_overrides` (
                `local_media_id` INTEGER NOT NULL,
                `is_abandoned` INTEGER NOT NULL,
                PRIMARY KEY(`local_media_id`),
                FOREIGN KEY(`local_media_id`) REFERENCES `media_entries`(`local_media_id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `media_genres` ADD COLUMN `source` TEXT")
        db.execSQL("ALTER TABLE `media_genres` ADD COLUMN `genre_id` INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_genres_source_genre_id` " +
                "ON `media_genres` (`source`, `genre_id`)"
        )
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
