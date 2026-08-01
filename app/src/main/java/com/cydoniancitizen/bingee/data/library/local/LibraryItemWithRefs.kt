package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation
import java.time.Instant

internal data class LibraryItemWithRefs(
    @Embedded
    val media: MediaEntity,
    @ColumnInfo(name = "membership_added_at")
    val addedAt: Instant,
    @Relation(
        parentColumn = "local_media_id",
        entityColumn = "local_media_id"
    )
    val externalRefs: List<ExternalRefEntity>
)
