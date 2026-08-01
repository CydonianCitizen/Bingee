package com.cydoniancitizen.bingee.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal interface FirstRunPreferences {
    suspend fun isOnboardingComplete(): Boolean

    suspend fun markOnboardingComplete()
}

private val Context.bingeePreferences by preferencesDataStore(name = "bingee_preferences")

@Singleton
internal class DataStoreFirstRunPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) : FirstRunPreferences {
    override suspend fun isOnboardingComplete(): Boolean = context.bingeePreferences.data
        .catch { failure ->
            if (failure is IOException) {
                emit(
                    androidx.datastore.preferences.core.emptyPreferences()
                )
            } else {
                throw failure
            }
        }.map { preferences -> preferences[ONBOARDING_COMPLETE] ?: false }
        .first()

    override suspend fun markOnboardingComplete() {
        context.bingeePreferences.edit { preferences ->
            preferences[ONBOARDING_COMPLETE] = true
        }
    }

    private companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }
}
