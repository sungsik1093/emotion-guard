/**
 * 감정 가드 테마: 색상 토큰, 상태 색, 타이포그래피.
 * 브랜드 일관성을 위해 다이내믹 컬러(배경화면 색)는 쓰지 않는다.
 */
package com.emotionguard.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.emotionguard.core.model.Level
import com.emotionguard.core.model.MoodState

object BrandColors {
    val Primary = Color(0xFF5B5BD6)
    val PrimaryDark = Color(0xFFB4B4FF)
    val Safe = Color(0xFF2F9E6B)
    val Caution = Color(0xFFF08C00)
    val Danger = Color(0xFFE03131)
    val Praise = Color(0xFFE64980)
    val Idle = Color(0xFF7286A0)
    val Offline = Color(0xFF9AA0A6)
}

fun Level.color(): Color = when (this) {
    Level.SAFE -> BrandColors.Safe
    Level.CAUTION -> BrandColors.Caution
    Level.DANGER -> BrandColors.Danger
}

fun MoodState.color(): Color = when (this) {
    MoodState.IDLE, MoodState.THINKING -> BrandColors.Idle
    MoodState.SAFE -> BrandColors.Safe
    MoodState.CAUTION -> BrandColors.Caution
    MoodState.DANGER -> BrandColors.Danger
    MoodState.PRAISE -> BrandColors.Praise
    MoodState.OFFLINE -> BrandColors.Offline
}

private val LightColors = lightColorScheme(
    primary = BrandColors.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4FF),
    onPrimaryContainer = Color(0xFF1B1B5E),
    secondary = Color(0xFF5E5C71),
    background = Color(0xFFFBFAFF),
    surface = Color(0xFFFBFAFF),
    surfaceVariant = Color(0xFFE6E4F0),
    surfaceContainer = Color(0xFFF1EFF8),
    surfaceContainerHigh = Color(0xFFEBE9F3),
    error = BrandColors.Danger,
)

private val DarkColors = darkColorScheme(
    primary = BrandColors.PrimaryDark,
    onPrimary = Color(0xFF1B1B5E),
    primaryContainer = Color(0xFF3B3BA8),
    onPrimaryContainer = Color(0xFFE4E4FF),
    secondary = Color(0xFFC8C4DC),
    background = Color(0xFF131318),
    surface = Color(0xFF131318),
    surfaceVariant = Color(0xFF46464F),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF29292F),
    error = Color(0xFFFF8787),
)

@Composable
fun EmotionGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
