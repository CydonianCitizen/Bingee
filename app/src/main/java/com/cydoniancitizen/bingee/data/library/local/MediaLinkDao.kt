package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant
import kotlinx.coroutines.flow.Flow

internal data class MediaLinkMemberWithIdentity(
    @ColumnInfo(name = "local_group_id") val localGroupId: Long,
    @ColumnInfo(name = "local_media_id") val localMediaId: Long,
    @ColumnInfo(name = "added_at") val addedAt: Instant,
    val source: MediaSource,
    @ColumnInfo(name = "media_type") val mediaType: MediaType,
    @ColumnInfo(name = "external_id") val externalId: String
)

internal data class MediaLinkGroupWithMembers(
    val group: MediaLinkGroupEntity,
    val members: List<MediaLinkMemberWithIdentity>
)

@Dao
internal abstract class MediaLinkDao {

    @Query(
        """
        SELECT media_entries.local_media_id FROM media_entries
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source
          AND external_refs.external_id = :externalId
          AND media_entries.media_type = :mediaType
        LIMIT 1
        """
    )
    abstract suspend fun findMediaIdByIdentity(source: MediaSource, mediaType: MediaType, externalId: String): Long?

    @Query("SELECT * FROM media_link_groups ORDER BY group_uuid ASC")
    abstract suspend fun getGroupEntities(): List<MediaLinkGroupEntity>

    @Query("SELECT * FROM media_link_groups WHERE group_uuid = :groupUuid LIMIT 1")
    abstract suspend fun findGroupEntityByUuid(groupUuid: String): MediaLinkGroupEntity?

    @Query(
        """
        SELECT media_link_groups.* FROM media_link_groups
        INNER JOIN media_link_members USING(local_group_id)
        WHERE media_link_members.local_media_id = :localMediaId
        LIMIT 1
        """
    )
    abstract suspend fun findGroupEntityByMediaId(localMediaId: Long): MediaLinkGroupEntity?

    @Query(
        """
        SELECT media_link_members.local_group_id, media_link_members.local_media_id,
               media_link_members.added_at, external_refs.source, media_entries.media_type,
               external_refs.external_id
        FROM media_link_members
        INNER JOIN media_entries USING(local_media_id)
        INNER JOIN external_refs USING(local_media_id)
        WHERE media_link_members.local_group_id = :localGroupId
        ORDER BY external_refs.source, external_refs.external_id
        """
    )
    abstract suspend fun findMembersWithIdentityByGroupId(localGroupId: Long): List<MediaLinkMemberWithIdentity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertGroup(group: MediaLinkGroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMembers(members: List<MediaLinkMemberEntity>)

    @Query(
        """
        UPDATE media_link_groups
        SET preferred_presentation_media_id = :preferredMediaId,
            updated_at = :updatedAt
        WHERE local_group_id = :localGroupId
        """
    )
    abstract suspend fun updatePreferredPresentation(localGroupId: Long, preferredMediaId: Long, updatedAt: Instant)

    @Query("DELETE FROM media_link_groups WHERE local_group_id = :localGroupId")
    abstract suspend fun deleteGroup(localGroupId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAudit(audit: MediaLinkAuditEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAuditMembers(members: List<MediaLinkAuditMemberEntity>)

    @Query(
        """
        SELECT media_link_members.local_group_id, media_link_members.local_media_id,
               media_link_members.added_at, external_refs.source, media_entries.media_type,
               external_refs.external_id
        FROM media_link_members
        INNER JOIN media_entries USING(local_media_id)
        INNER JOIN external_refs USING(local_media_id)
        WHERE media_link_members.local_group_id = (
            SELECT local_group_id FROM media_link_groups WHERE group_uuid = :groupUuid
        )
        ORDER BY external_refs.source, external_refs.external_id
        """
    )
    abstract fun observeMembersWithIdentityByGroupUuid(groupUuid: String): Flow<List<MediaLinkMemberWithIdentity>>

    @Query("SELECT * FROM media_link_groups WHERE group_uuid = :groupUuid LIMIT 1")
    abstract fun observeGroupEntityByUuid(groupUuid: String): Flow<MediaLinkGroupEntity?>

    @Query(
        """
        SELECT media_link_groups.* FROM media_link_groups
        INNER JOIN media_link_members USING(local_group_id)
        WHERE media_link_members.local_media_id = (
            SELECT media_entries.local_media_id FROM media_entries
            INNER JOIN external_refs USING(local_media_id)
            WHERE external_refs.source = :source
              AND external_refs.external_id = :externalId
              AND media_entries.media_type = :mediaType
            LIMIT 1
        )
        LIMIT 1
        """
    )
    abstract fun observeGroupEntityByMediaIdentity(
        source: MediaSource,
        mediaType: MediaType,
        externalId: String
    ): Flow<MediaLinkGroupEntity?>

    @Query(
        """
        SELECT media_link_members.local_group_id, media_link_members.local_media_id,
               media_link_members.added_at, external_refs.source, media_entries.media_type,
               external_refs.external_id
        FROM media_link_members
        INNER JOIN media_entries USING(local_media_id)
        INNER JOIN external_refs USING(local_media_id)
        WHERE media_link_members.local_group_id = (
            SELECT media_link_members.local_group_id FROM media_link_members
            INNER JOIN external_refs USING(local_media_id)
            INNER JOIN media_entries USING(local_media_id)
            WHERE external_refs.source = :source
              AND external_refs.external_id = :externalId
              AND media_entries.media_type = :mediaType
            LIMIT 1
        )
        ORDER BY external_refs.source, external_refs.external_id
        """
    )
    abstract fun observeMembersWithIdentityByMediaIdentity(
        source: MediaSource,
        mediaType: MediaType,
        externalId: String
    ): Flow<List<MediaLinkMemberWithIdentity>>

    @Query("SELECT * FROM media_link_audit ORDER BY audit_id ASC")
    abstract suspend fun getAuditTrail(): List<MediaLinkAuditEntity>

    @Query("SELECT * FROM media_link_audit_members WHERE audit_id = :auditId ORDER BY source, external_id")
    abstract suspend fun getAuditMembers(auditId: Long): List<MediaLinkAuditMemberEntity>
}
