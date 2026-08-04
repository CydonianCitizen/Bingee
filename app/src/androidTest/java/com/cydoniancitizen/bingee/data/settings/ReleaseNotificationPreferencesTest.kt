package com.cydoniancitizen.bingee.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseNotificationPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
    private val repository = DataStoreReleaseNotificationPreferences(context, database, database.portableSnapshotDao())

    @After
    fun closeDatabase() {
        database.close()
    }

    @Before
    fun resetDefaults() = runBlocking {
        repository.setEnabled(false)
        repository.setLeadTime(ReleaseNotificationLeadTime.ONE_DAY)
        repository.setMovieReleases(true)
        repository.setSeasonPremieres(true)
        repository.setEpisodeAirings(true)
    }

    @Test
    fun defaultsUpdatesAndFlowEmissionsRemainIndependent() = runBlocking {
        val defaults = repository.preferences.first()
        assertFalse(defaults.enabled)
        assertEquals(ReleaseNotificationLeadTime.ONE_DAY, defaults.leadTime)
        assertTrue(defaults.movieReleases && defaults.seasonPremieres && defaults.episodeAirings)

        repository.setEnabled(true)
        repository.setLeadTime(ReleaseNotificationLeadTime.SEVEN_DAYS)
        repository.setMovieReleases(false)
        repository.setSeasonPremieres(false)
        repository.setEpisodeAirings(false)
        val changed = repository.preferences.first()
        assertTrue(changed.enabled)
        assertEquals(ReleaseNotificationLeadTime.SEVEN_DAYS, changed.leadTime)
        assertFalse(changed.movieReleases || changed.seasonPremieres || changed.episodeAirings)
    }

    @Test
    fun corruptedLeadTimeFallsBackAndStoreContainsNoTokenOrTitleKeys() = runBlocking {
        context.bingeePreferences.edit {
            it[stringPreferencesKey("release_notifications_lead_time")] = "CORRUPTED"
        }
        assertEquals(ReleaseNotificationLeadTime.ONE_DAY, repository.preferences.first().leadTime)
        val keys = context.bingeePreferences.data.first().asMap().keys.map { it.name }
        assertTrue(keys.none { it.contains("token", true) || it.contains("title", true) })
    }
}
