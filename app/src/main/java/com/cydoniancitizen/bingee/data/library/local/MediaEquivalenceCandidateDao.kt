package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

internal data class RawCandidateProjection(
    val localMediaId: Long,
    val mediaType: MediaType,
    val title: String,
    val originalTitle: String?,
    val releaseDate: LocalDate?,
    val numberOfSeasons: Int?,
    val animeFormat: AnimeFormat?,
    val englishTitle: String?,
    val japaneseTitle: String?,
    val animeYear: Int?,
    val animeStartDate: LocalDate?,
    val isLibraryMember: Boolean
)

internal data class FullCandidateData(
    @Embedded val raw: RawCandidateProjection,
    @Relation(parentColumn = "localMediaId", entityColumn = "local_media_id")
    val externalRefs: List<ExternalRefEntity>,
    @Relation(parentColumn = "localMediaId", entityColumn = "local_media_id")
    val animeRelations: List<AnimeRelationEntity>
)

@Dao
internal abstract class MediaEquivalenceCandidateDao {

    @androidx.room.Transaction
    @Query(
        """
        SELECT 
            m.local_media_id AS localMediaId,
            m.media_type AS mediaType,
            m.title AS title,
            m.original_title AS originalTitle,
            m.release_date AS releaseDate,
            md.number_of_seasons AS numberOfSeasons,
            ad.format AS animeFormat,
            ad.english_title AS englishTitle,
            ad.japanese_title AS japaneseTitle,
            ad.year AS animeYear,
            ad.start_date AS animeStartDate,
            CASE WHEN le.local_media_id IS NOT NULL THEN 1 ELSE 0 END AS isLibraryMember
        FROM media_entries m
        LEFT JOIN media_details md ON m.local_media_id = md.local_media_id
        LEFT JOIN anime_details ad ON m.local_media_id = ad.local_media_id
        LEFT JOIN library_entries le ON m.local_media_id = le.local_media_id
    """
    )
    abstract fun observeAllCandidatesData(): Flow<List<FullCandidateData>>

    @androidx.room.Transaction
    @Query(
        """
        SELECT 
            m.local_media_id AS localMediaId,
            m.media_type AS mediaType,
            m.title AS title,
            m.original_title AS originalTitle,
            m.release_date AS releaseDate,
            md.number_of_seasons AS numberOfSeasons,
            ad.format AS animeFormat,
            ad.english_title AS englishTitle,
            ad.japanese_title AS japaneseTitle,
            ad.year AS animeYear,
            ad.start_date AS animeStartDate,
            CASE WHEN le.local_media_id IS NOT NULL THEN 1 ELSE 0 END AS isLibraryMember
        FROM media_entries m
        LEFT JOIN media_details md ON m.local_media_id = md.local_media_id
        LEFT JOIN anime_details ad ON m.local_media_id = ad.local_media_id
        LEFT JOIN library_entries le ON m.local_media_id = le.local_media_id
    """
    )
    abstract suspend fun getAllCandidatesData(): List<FullCandidateData>

    @Query("SELECT local_media_id FROM media_link_members")
    abstract fun observeActiveLinkMemberMediaIds(): Flow<List<Long>>

    @Query("SELECT local_media_id FROM media_link_members")
    abstract suspend fun getActiveLinkMemberMediaIds(): List<Long>
}
