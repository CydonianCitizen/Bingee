package com.cydoniancitizen.bingee.data.settings

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.bingeePreferences by preferencesDataStore(name = "bingee_preferences")
