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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `media_entries` ADD COLUMN `favorite_added_at` TEXT")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Movie runtime joins the portable media row, seeded from the Details cache, so it survives a restore.
        db.execSQL("ALTER TABLE `media_entries` ADD COLUMN `runtime_minutes` INTEGER")
        db.execSQL(
            """
            UPDATE `media_entries` SET `runtime_minutes` = (
                SELECT `runtime_minutes` FROM `media_details`
                WHERE `media_details`.`local_media_id` = `media_entries`.`local_media_id`
            )
            WHERE `media_type` = 'MOVIE'
            """.trimIndent()
        )
        // TMDB numbers movies and series independently, so the type joins the identity key. SQLite cannot
        // change a primary key in place: rebuild the table, giving each reference its media's type.
        db.execSQL(
            """
            CREATE TABLE `external_refs_new` (
                `local_media_id` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                `media_type` TEXT NOT NULL,
                `external_id` TEXT NOT NULL,
                PRIMARY KEY(`source`, `media_type`, `external_id`),
                FOREIGN KEY(`local_media_id`) REFERENCES `media_entries`(`local_media_id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `external_refs_new` (`local_media_id`, `source`, `media_type`, `external_id`)
            SELECT `external_refs`.`local_media_id`, `external_refs`.`source`, `media_entries`.`media_type`,
                   `external_refs`.`external_id`
            FROM `external_refs`
            INNER JOIN `media_entries` USING(`local_media_id`)
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `external_refs`")
        db.execSQL("ALTER TABLE `external_refs_new` RENAME TO `external_refs`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_external_refs_local_media_id` ON `external_refs` (`local_media_id`)"
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Background refresh keeps each title's last attempt apart from its last success, so titles that
        // keep failing rotate behind the others instead of filling every batch.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `background_refresh_attempts` (
                `local_media_id` INTEGER NOT NULL,
                `attempted_at` TEXT NOT NULL,
                PRIMARY KEY(`local_media_id`),
                FOREIGN KEY(`local_media_id`) REFERENCES `media_entries`(`local_media_id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        // Cached TMDB text records the language it was fetched in. Existing rows stay unknown and age out.
        db.execSQL("ALTER TABLE `media_details` ADD COLUMN `language` TEXT")
        db.execSQL("ALTER TABLE `seasons` ADD COLUMN `episodes_language` TEXT")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `seasons` ADD COLUMN `is_known_empty` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            UPDATE `seasons` SET `is_known_empty` = 1
            WHERE `episode_count` = 0 AND `episodes_fetched_at` IS NOT NULL
                AND NOT EXISTS (
                    SELECT 1 FROM `episodes` WHERE `episodes`.`local_season_id` = `seasons`.`local_season_id`
                )
            """.trimIndent()
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `portable_preferences` ADD COLUMN `theme` TEXT NOT NULL DEFAULT 'SYSTEM_DEFAULT'")
        db.execSQL("ALTER TABLE `portable_preferences` ADD COLUMN `language` TEXT NOT NULL DEFAULT 'ENGLISH'")
        db.execSQL("ALTER TABLE `portable_preferences` ADD COLUMN `hide_episode_spoilers` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "ALTER TABLE `portable_preferences` ADD COLUMN `watched_movies_display_mode` TEXT NOT NULL DEFAULT 'LIST'"
        )
        db.execSQL(
            "ALTER TABLE `portable_preferences` ADD COLUMN `watched_tv_series_display_mode` TEXT NOT NULL DEFAULT 'LIST'"
        )
        db.execSQL(
            "ALTER TABLE `portable_preferences` ADD COLUMN `watch_later_movies_display_mode` TEXT NOT NULL DEFAULT 'LIST'"
        )
        db.execSQL(
            "ALTER TABLE `portable_preferences` ADD COLUMN `watch_later_tv_series_display_mode` TEXT NOT NULL DEFAULT 'LIST'"
        )
        db.execSQL(
            "ALTER TABLE `portable_preferences` ADD COLUMN `favorites_movies_display_mode` TEXT NOT NULL DEFAULT 'LIST'"
        )
        db.execSQL(
            "ALTER TABLE `portable_preferences` ADD COLUMN `favorites_tv_series_display_mode` TEXT NOT NULL DEFAULT 'LIST'"
        )
        db.execSQL(
            "ALTER TABLE `portable_preferences` ADD COLUMN `legacy_settings_bridge_completed` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `notification_deliveries` ADD COLUMN `claim_token` TEXT")
        db.execSQL("ALTER TABLE `notification_deliveries` ADD COLUMN `claim_expires_at_ms` INTEGER")
    }
}

val ALL_MIGRATIONS: Array<Migration> =
    arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9
    )
