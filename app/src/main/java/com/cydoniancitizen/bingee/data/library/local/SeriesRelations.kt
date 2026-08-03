package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Embedded
import androidx.room.Relation

internal data class EpisodeWithProgressRelation(
    @Embedded val episode: EpisodeEntity,
    @Relation(parentColumn = "local_episode_id", entityColumn = "local_episode_id")
    val progress: EpisodeWatchProgressEntity?
)

internal data class SeasonWithEpisodesRelation(
    @Embedded val season: SeasonEntity,
    @Relation(
        entity = EpisodeEntity::class,
        parentColumn = "local_season_id",
        entityColumn = "local_season_id"
    )
    val episodes: List<EpisodeWithProgressRelation>
)
