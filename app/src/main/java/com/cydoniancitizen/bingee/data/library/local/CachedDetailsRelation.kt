package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Embedded
import androidx.room.Relation

internal data class CachedDetailsRelation(
    @Embedded val media: MediaEntity,
    @Relation(parentColumn = "local_media_id", entityColumn = "local_media_id")
    val details: MediaDetailsEntity?,
    @Relation(parentColumn = "local_media_id", entityColumn = "local_media_id")
    val genres: List<MediaGenreEntity>,
    @Relation(parentColumn = "local_media_id", entityColumn = "local_media_id")
    val externalRefs: List<ExternalRefEntity>
)
