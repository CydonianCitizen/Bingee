package com.cydoniancitizen.bingee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cydoniancitizen.bingee.app.BingeeApp
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BingeeTheme {
                BingeeApp()
            }
        }
    }
}
