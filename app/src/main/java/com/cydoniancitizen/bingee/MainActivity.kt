package com.cydoniancitizen.bingee

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cydoniancitizen.bingee.app.BingeeApp
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.data.notification.NotificationDetailIntent
import com.cydoniancitizen.bingee.data.notification.NotificationNavigationTarget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val notificationTarget = MutableStateFlow<NotificationNavigationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationTarget.value = NotificationDetailIntent.parse(intent)
        enableEdgeToEdge()
        setContent {
            BingeeTheme {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTarget.value = NotificationDetailIntent.parse(intent)
    }
}
