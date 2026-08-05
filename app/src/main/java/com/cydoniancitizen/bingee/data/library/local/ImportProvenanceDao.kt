package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

internal enum class ImportProvenanceWriteOutcome { INSERTED, ALREADY_PRESENT }

@Dao
internal abstract class ImportProvenanceDao {
    @Query(
        """
        SELECT * FROM import_provenance_refs
        WHERE namespace = :namespace AND external_id = :externalId AND target_type = :targetType
        LIMIT 1
        """
    )
    abstract suspend fun find(namespace: String, externalId: String, targetType: String): ImportProvenanceRefEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(ref: ImportProvenanceRefEntity): Long

    suspend fun add(ref: ImportProvenanceRefEntity): ImportProvenanceWriteOutcome {
        val existing = find(ref.namespace, ref.externalId, ref.targetType)
        if (existing != null) {
            check(existing == ref) { "Import provenance identity belongs to another local entity" }
            return ImportProvenanceWriteOutcome.ALREADY_PRESENT
        }
        check(insert(ref) != -1L) { "Import provenance identity was claimed concurrently" }
        return ImportProvenanceWriteOutcome.INSERTED
    }
}
