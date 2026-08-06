package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cydoniancitizen.bingee.core.model.MediaLinkAuditAction
import com.cydoniancitizen.bingee.core.model.MediaLinkAuditOrigin
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant

@Entity(
    tableName = "media_link_groups",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["local_media_id"],
            childColumns = ["preferred_presentation_media_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["group_uuid"], unique = true),
        Index(value = ["preferred_presentation_media_id"])
    ]
)
internal data class MediaLinkGroupEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_group_id")
    val localGroupId: Long = 0,
    @ColumnInfo(name = "group_uuid")
    val groupUuid: String,
    @ColumnInfo(name = "preferred_presentation_media_id")
    val preferredPresentationMediaId: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant
) {
    init {
        require(localGroupId >= 0) { "Local group ID must not be negative" }
        require(groupUuid.isNotBlank()) { "Group UUID must not be blank" }
        require(preferredPresentationMediaId > 0) { "Preferred presentation media ID must be positive" }
    }
}

@Entity(
    tableName = "media_link_members",
    primaryKeys = ["local_group_id", "local_media_id"],
    foreignKeys = [
        ForeignKey(
            entity = MediaLinkGroupEntity::class,
            parentColumns = ["local_group_id"],
            childColumns = ["local_group_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["local_media_id"],
            childColumns = ["local_media_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["local_group_id"]),
        Index(value = ["local_media_id"], unique = true)
    ]
)
internal data class MediaLinkMemberEntity(
    @ColumnInfo(name = "local_group_id")
    val localGroupId: Long,
    @ColumnInfo(name = "local_media_id")
    val localMediaId: Long,
    @ColumnInfo(name = "added_at")
    val addedAt: Instant
) {
    init {
        require(localGroupId > 0) { "Local group ID must be positive" }
        require(localMediaId > 0) { "Local media ID must be positive" }
    }
}

@Entity(
    tableName = "media_link_audit",
    indices = [
        Index(value = ["group_uuid"])
    ]
)
internal data class MediaLinkAuditEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "audit_id")
    val auditId: Long = 0,
    @ColumnInfo(name = "group_uuid")
    val groupUuid: String,
    val action: MediaLinkAuditAction,
    @ColumnInfo(name = "action_timestamp")
    val actionTimestamp: Instant,
    val origin: MediaLinkAuditOrigin,
    @ColumnInfo(name = "preferred_source")
    val preferredSource: MediaSource? = null,
    @ColumnInfo(name = "preferred_media_type")
    val preferredMediaType: MediaType? = null,
    @ColumnInfo(name = "preferred_external_id")
    val preferredExternalId: String? = null
) {
    init {
        require(auditId >= 0) { "Audit ID must not be negative" }
        require(groupUuid.isNotBlank()) { "Group UUID must not be blank" }
    }
}

@Entity(
    tableName = "media_link_audit_members",
    primaryKeys = ["audit_id", "source", "media_type", "external_id"],
    foreignKeys = [
        ForeignKey(
            entity = MediaLinkAuditEntity::class,
            parentColumns = ["audit_id"],
            childColumns = ["audit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["audit_id"])
    ]
)
internal data class MediaLinkAuditMemberEntity(
    @ColumnInfo(name = "audit_id")
    val auditId: Long,
    val source: MediaSource,
    @ColumnInfo(name = "media_type")
    val mediaType: MediaType,
    @ColumnInfo(name = "external_id")
    val externalId: String
) {
    init {
        require(auditId > 0) { "Audit ID must be positive" }
        require(externalId.isNotBlank()) { "External ID must not be blank" }
    }
}
