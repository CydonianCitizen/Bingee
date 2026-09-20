package com.cydoniancitizen.bingee.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

internal val Context.bingeePreferences by preferencesDataStore(name = "bingee_preferences")

/** The stored preferences, read as empty rather than failing when the file cannot be read. */
internal val Context.bingeePreferenceData: Flow<Preferences>
    get() = bingeePreferences.data.catch { failure ->
        if (failure is IOException) emit(emptyPreferences()) else throw failure
    }
