package com.cydoniancitizen.bingee.data.jikan.details

import androidx.room.withTransaction
import com.cydoniancitizen.bingee.core.model.AnimeDetails
import com.cydoniancitizen.bingee.data.calendar.ReleaseEventProjector
import com.cydoniancitizen.bingee.data.library.local.AnimeDao
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.ReleaseEventDao
import java.time.Instant
import javax.inject.Inject

internal class AnimeMetadataStore @Inject constructor(
    private val database: BingeeDatabase,
    private val animeDao: AnimeDao,
    private val releaseEventDao: ReleaseEventDao,
    private val projector: ReleaseEventProjector
) {
    suspend fun store(details: AnimeDetails, fetchedAt: Instant) {
        val write = details.toCacheWrite(fetchedAt)
        database.withTransaction {
            animeDao.storeAnime(write.media, details.externalRef.externalId, write.details, write.relations)
            releaseEventDao.reconcileAnime(details.externalRef, projector.anime(details, fetchedAt))
        }
    }
}
