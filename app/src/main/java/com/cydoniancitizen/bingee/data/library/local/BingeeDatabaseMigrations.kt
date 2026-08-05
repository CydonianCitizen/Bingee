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

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS release_events (
                local_event_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                local_media_id INTEGER NOT NULL,
                local_season_id INTEGER,
                local_episode_id INTEGER,
                source TEXT NOT NULL,
                subject_type TEXT NOT NULL,
                subject_external_id TEXT NOT NULL,
                event_type TEXT NOT NULL,
                event_date TEXT NOT NULL,
                projected_at TEXT NOT NULL,
                source_metadata_updated_at TEXT NOT NULL,
                FOREIGN KEY(local_media_id) REFERENCES media_entries(local_media_id)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(local_season_id) REFERENCES seasons(local_season_id)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(local_episode_id) REFERENCES episodes(local_episode_id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_release_events_local_media_id ON release_events(local_media_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_release_events_local_season_id ON release_events(local_season_id)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_release_events_local_episode_id ON release_events(local_episode_id)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_release_events_event_date ON release_events(event_date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_release_events_event_type ON release_events(event_type)")
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                index_release_events_source_subject_type_subject_external_id_event_type
            ON release_events(source, subject_type, subject_external_id, event_type)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS calendar_refresh_state (
                singleton_key INTEGER NOT NULL,
                last_successful_refresh_at TEXT NOT NULL,
                PRIMARY KEY(singleton_key)
            )
            """.trimIndent()
        )
        backfillReleaseEvents(db)
    }

    private fun backfillReleaseEvents(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO release_events(
                local_media_id, local_season_id, local_episode_id, source,
                subject_type, subject_external_id, event_type, event_date,
                projected_at, source_metadata_updated_at
            )
            SELECT media_entries.local_media_id, NULL, NULL, external_refs.source,
                   'MEDIA', external_refs.external_id, 'MOVIE_RELEASE', media_entries.release_date,
                   media_entries.metadata_updated_at, media_entries.metadata_updated_at
            FROM media_entries
            INNER JOIN external_refs USING(local_media_id)
            WHERE media_entries.media_type = 'MOVIE'
              AND media_entries.release_date IS NOT NULL
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO release_events(
                local_media_id, local_season_id, local_episode_id, source,
                subject_type, subject_external_id, event_type, event_date,
                projected_at, source_metadata_updated_at
            )
            SELECT seasons.local_media_id, seasons.local_season_id, NULL, seasons.source,
                   'SEASON', seasons.external_id, 'SEASON_PREMIERE', seasons.air_date,
                   seasons.metadata_updated_at, seasons.metadata_updated_at
            FROM seasons
            WHERE seasons.air_date IS NOT NULL
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO release_events(
                local_media_id, local_season_id, local_episode_id, source,
                subject_type, subject_external_id, event_type, event_date,
                projected_at, source_metadata_updated_at
            )
            SELECT seasons.local_media_id, episodes.local_season_id, episodes.local_episode_id,
                   episodes.source, 'EPISODE', episodes.external_id, 'EPISODE_AIRING',
                   episodes.air_date, episodes.metadata_updated_at, episodes.metadata_updated_at
            FROM episodes
            INNER JOIN seasons USING(local_season_id)
            WHERE episodes.air_date IS NOT NULL
            """.trimIndent()
        )
    }
}

internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notification_deliveries (
                source TEXT NOT NULL,
                subject_type TEXT NOT NULL,
                subject_external_id TEXT NOT NULL,
                event_type TEXT NOT NULL,
                event_date TEXT NOT NULL,
                lead_days INTEGER NOT NULL,
                notification_id INTEGER NOT NULL,
                delivered_at TEXT NOT NULL,
                PRIMARY KEY(source, subject_type, subject_external_id, event_type, event_date, lead_days)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_notification_deliveries_event_date " +
                "ON notification_deliveries(event_date)"
        )
    }
}

internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `portable_preferences` (
                `singleton_key` INTEGER NOT NULL,
                `notification_lead_days` INTEGER NOT NULL,
                `notify_movie_releases` INTEGER NOT NULL,
                `notify_season_premieres` INTEGER NOT NULL,
                `notify_episode_airings` INTEGER NOT NULL,
                `legacy_bridge_completed` INTEGER NOT NULL,
                PRIMARY KEY(`singleton_key`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO portable_preferences(
                singleton_key, notification_lead_days, notify_movie_releases,
                notify_season_premieres, notify_episode_airings, legacy_bridge_completed
            ) VALUES (1, 1, 1, 1, 1, 0)
            """.trimIndent()
        )
    }
}

internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `import_provenance_refs` (
                `namespace` TEXT NOT NULL,
                `external_id` TEXT NOT NULL,
                `target_type` TEXT NOT NULL,
                `local_media_id` INTEGER,
                `local_season_id` INTEGER,
                `local_episode_id` INTEGER,
                PRIMARY KEY(`namespace`, `external_id`, `target_type`),
                FOREIGN KEY(`local_media_id`) REFERENCES `media_entries`(`local_media_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`local_season_id`) REFERENCES `seasons`(`local_season_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`local_episode_id`) REFERENCES `episodes`(`local_episode_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                CHECK ((`local_media_id` IS NOT NULL) + (`local_season_id` IS NOT NULL) + (`local_episode_id` IS NOT NULL) = 1)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_import_provenance_refs_local_media_id` ON `import_provenance_refs` (`local_media_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_import_provenance_refs_local_season_id` ON `import_provenance_refs` (`local_season_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_import_provenance_refs_local_episode_id` ON `import_provenance_refs` (`local_episode_id`)"
        )
    }
}
