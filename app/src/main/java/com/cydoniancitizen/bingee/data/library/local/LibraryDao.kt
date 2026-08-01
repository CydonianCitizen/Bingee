package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class LibraryDao {
    @Transaction
    @Query(
        """
        SELECT media_entries.*, library_entries.added_at AS membership_added_at
        FROM media_entries
        INNER JOIN library_entries USING(local_media_id)
        ORDER BY library_entries.added_at DESC, media_entries.title COLLATE NOCASE ASC
        """
    )
    abstract fun observeLibraryItems(): Flow<List<LibraryItemWithRefs>>

    @Transaction
    @Query(
        """
        SELECT media_entries.*, library_entries.added_at AS membership_added_at
        FROM media_entries
        INNER JOIN library_entries USING(local_media_id)
        WHERE media_entries.media_type = :mediaType
        ORDER BY library_entries.added_at DESC, media_entries.title COLLATE NOCASE ASC
        """
    )
    abstract fun observeLibraryItems(mediaType: MediaType): Flow<List<LibraryItemWithRefs>>

    @Transaction
    @Query(
        """
        SELECT media_entries.*, library_entries.added_at AS membership_added_at
        FROM media_entries
        INNER JOIN library_entries USING(local_media_id)
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source AND external_refs.external_id = :externalId
        LIMIT 1
        """
    )
    abstract fun observeLibraryItem(source: MediaSource, externalId: String): Flow<LibraryItemWithRefs?>

    @Query(
        """
        SELECT external_refs.*
        FROM external_refs
        INNER JOIN library_entries USING(local_media_id)
        ORDER BY external_refs.source, external_refs.external_id
        """
    )
    abstract fun observeMembershipRefs(): Flow<List<ExternalRefEntity>>

    @Query(
        """
        SELECT media_entries.*
        FROM media_entries
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source AND external_refs.external_id = :externalId
        LIMIT 1
        """
    )
    abstract suspend fun getMediaByExternalRef(source: MediaSource, externalId: String): MediaEntity?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM external_refs
            INNER JOIN library_entries USING(local_media_id)
            WHERE external_refs.source = :source AND external_refs.external_id = :externalId
        )
        """
    )
    abstract suspend fun isInLibrary(source: MediaSource, externalId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMedia(media: MediaEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun updateMedia(media: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertExternalRef(externalRef: ExternalRefEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertMembership(membership: LibraryMembershipEntity)

    @Transaction
    @Query(
        """
        SELECT media_entries.*, library_entries.added_at AS membership_added_at
        FROM media_entries
        INNER JOIN library_entries USING(local_media_id)
        WHERE media_entries.local_media_id = :localMediaId
        LIMIT 1
        """
    )
    protected abstract suspend fun getLibraryItem(localMediaId: Long): LibraryItemWithRefs?

    @Transaction
    open suspend fun addToLibrary(
        candidate: MediaEntity,
        source: MediaSource,
        externalId: String,
        addedAt: Instant
    ): LibraryItemWithRefs {
        val existing = getMediaByExternalRef(source, externalId)
        val localMediaId =
            if (existing == null) {
                val insertedId = insertMedia(candidate)
                insertExternalRef(
                    ExternalRefEntity(
                        localMediaId = insertedId,
                        source = source,
                        externalId = externalId
                    )
                )
                insertedId
            } else {
                check(existing.mediaType == candidate.mediaType) {
                    "External identity is already assigned to another media type"
                }
                updateMedia(
                    candidate.copy(
                        localMediaId = existing.localMediaId,
                        createdAt = existing.createdAt
                    )
                )
                existing.localMediaId
            }

        insertMembership(LibraryMembershipEntity(localMediaId, addedAt))
        return checkNotNull(getLibraryItem(localMediaId)) {
            "Library transaction completed without a readable membership"
        }
    }

    @Query(
        """
        DELETE FROM library_entries
        WHERE local_media_id = (
            SELECT local_media_id
            FROM external_refs
            WHERE source = :source AND external_id = :externalId
        )
        """
    )
    abstract suspend fun removeMembership(source: MediaSource, externalId: String): Int
}
