package com.cydoniancitizen.bingee

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
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
        installSplashScreen()
        super.onCreate(savedInstanceState)
        notificationTarget.value = NotificationDetailIntent.parse(intent)
        enableEdgeToEdge()
        lifecycleScope.launch {
            appearancePreferences.observeLanguage()
                .collect(::applyAppLanguage)
        }
        lifecycleScope.launch {
            appearancePreferences.observeTheme()
                .collect(::applyNightMode)
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

            SideEffect {
                WindowInsetsControllerCompat(window, window.decorView).run {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
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

    /**
     * Keeps the AppCompat night mode on the same preference the Compose theme reads.
     *
     * [BingeeTheme] only decides which `ColorScheme` Compose draws with. Resources resolved through
     * the view theme — `?attr/` tints in vector drawables, the window background — follow
     * `Theme.Material3.DayNight` instead, which without this call stays on the system setting. A
     * user who forces Dark while the system is Light would otherwise get dark Compose surfaces and
     * light-theme drawable tints on top of them.
     */
    private fun applyNightMode(theme: AppTheme) {
        val mode = when (theme) {
            AppTheme.SYSTEM_DEFAULT -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppTheme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
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
