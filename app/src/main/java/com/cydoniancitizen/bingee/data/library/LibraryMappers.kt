package com.cydoniancitizen.bingee.data.library

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.data.library.local.ExternalRefEntity
import com.cydoniancitizen.bingee.data.library.local.LibraryItemWithRefs
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import java.time.Instant

internal fun MediaSearchResult.toMediaEntity(now: Instant): MediaEntity = MediaEntity(
    mediaType = mediaType,
    title = title.trim().also { require(it.isNotEmpty()) { "Media title must not be blank" } },
    originalTitle = originalTitle.normalizedOptionalText(),
    overview = overview.normalizedOptionalText(),
    posterUrl = posterUrl.normalizedOptionalText(),
    releaseDate = releaseDate,
    createdAt = now,
    metadataUpdatedAt = now
)

internal fun LibraryItemWithRefs.toDomain(preferredRef: ExternalMediaRef? = null): LibraryEntry {
    val refs = externalRefs.map(ExternalRefEntity::toDomain)
    require(refs.isNotEmpty()) { "Persisted library item has no external reference" }
    val selectedRef =
        preferredRef?.takeIf(refs::contains)
            ?: refs.minWith(compareBy<ExternalMediaRef> { it.source.name }.thenBy { it.externalId })
    return LibraryEntry(
        mediaRef = selectedRef,
        mediaType = media.mediaType,
        title = media.title,
        originalTitle = media.originalTitle,
        posterUrl = media.posterUrl,
        releaseDate = media.releaseDate,
        overview = media.overview,
        addedAt = addedAt
    )
}

internal fun ExternalRefEntity.toDomain(): ExternalMediaRef = ExternalMediaRef(source = source, externalId = externalId)

private fun String?.normalizedOptionalText(): String? = this?.trim()?.takeIf(String::isNotEmpty)
