package com.cydoniancitizen.bingee

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.cydoniancitizen.bingee.app.BingeeApp
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.data.notification.NotificationDetailIntent
import com.cydoniancitizen.bingee.data.notification.NotificationNavigationTarget
import com.cydoniancitizen.bingee.data.settings.AppLanguage
import com.cydoniancitizen.bingee.data.settings.AppTheme
import com.cydoniancitizen.bingee.data.settings.AppearancePreferences
import com.cydoniancitizen.bingee.data.settings.toApplicationLocales
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var appearancePreferences: AppearancePreferences

    private val notificationTarget = MutableStateFlow<NotificationNavigationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationTarget.value = NotificationDetailIntent.parse(intent)
        enableEdgeToEdge()
        lifecycleScope.launch {
            appearancePreferences.observeLanguage()
                .collect(::applyAppLanguage)
        }
        setContent {
            val theme by appearancePreferences.observeTheme().collectAsStateWithLifecycle(
                initialValue = AppTheme.SYSTEM_DEFAULT
            )
            val darkTheme = when (theme) {
                AppTheme.SYSTEM_DEFAULT -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }

            BingeeTheme(darkTheme = darkTheme) {
                BingeeApp(
                    notificationTarget = notificationTarget,
                    onNotificationTargetConsumed = {
                        notificationTarget.value = null
                        setIntent(Intent(this, MainActivity::class.java))
                    }
                )
            }
        }
    }

    private fun applyAppLanguage(language: AppLanguage) {
        val locales = language.toApplicationLocales()
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTarget.value = NotificationDetailIntent.parse(intent)
    }
}
