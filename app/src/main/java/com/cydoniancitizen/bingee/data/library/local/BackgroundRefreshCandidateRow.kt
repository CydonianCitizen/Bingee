package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType

internal data class BackgroundRefreshCandidateRow(
    val source: MediaSource,
    @ColumnInfo(name = "external_id") val externalId: String,
    @ColumnInfo(name = "media_type") val mediaType: MediaType
)
