package com.emotionguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.emotionguard.app.navigation.EmotionGuardApp
import com.emotionguard.core.designsystem.theme.EmotionGuardTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EmotionGuardTheme {
                EmotionGuardApp()
            }
        }
    }
}
