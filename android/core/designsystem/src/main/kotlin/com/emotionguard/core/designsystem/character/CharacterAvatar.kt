/**
 * 캐릭터 한 개를 그린다: 사진 또는 기본 캐릭터 + 상태 색 테두리 + 상태별 움직임.
 *
 * 렌더링 우선순위 (디자인 문서 §6.3)
 *   상태별 사진 → 사진 한 장 → 기본 캐릭터
 * 사진을 바꿔도 테두리 색과 움직임은 그대로라서 지금 상태를 알 수 있다.
 */
package com.emotionguard.core.designsystem.character

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.emotionguard.core.designsystem.theme.color
import com.emotionguard.core.model.Accessory
import com.emotionguard.core.model.CharacterSkin
import com.emotionguard.core.model.MoodState
import java.io.File

@Composable
fun CharacterAvatar(
    state: MoodState,
    skin: CharacterSkin,
    resolveImage: (String) -> File,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    accessory: Accessory = Accessory.NONE,
) {
    val ringColor by animateColorAsState(state.color(), tween(300), label = "ring")
    val motion = rememberMotion(state, enabled = skin.animate)
    val imageName = skin.imageFor(state)
    val ringWidth = if (skin.showColorRing) (size.value * 0.06f).coerceAtLeast(2f).dp else 0.dp
    val description = buildString {
        append("캐릭터, ").append(state.label)
        if (accessory != Accessory.NONE) append(", ").append(accessory.label).append(" 장식")
    }

    // 바깥 상자: 움직임과 장식. 안쪽 원: 얼굴(사진 또는 기본 캐릭터)과 상태 색 테두리
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description }
            .graphicsLayer {
                scaleX = motion.scale
                scaleY = motion.scale
                translationX = motion.shakeX * density
                translationY = motion.bounceY * density
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .then(if (skin.showColorRing) Modifier.border(ringWidth, ringColor, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val inner = Modifier.fillMaxSize().padding(ringWidth).clip(CircleShape)
            if (imageName != null) {
                AsyncImage(
                    model = resolveImage(imageName),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = inner,
                )
            } else {
                DefaultCharacter(state, inner)
            }
        }
        if (accessory != Accessory.NONE) AccessoryLayer(accessory, Modifier.fillMaxSize())
    }
}
private data class Motion(val scale: Float, val shakeX: Float, val bounceY: Float)

/** 상태별 움직임: 대기는 숨쉬기, 안전은 통통, 주의·위험은 흔들림, 칭찬은 두근 */
@Composable
private fun rememberMotion(state: MoodState, enabled: Boolean): Motion {
    val transition = rememberInfiniteTransition(label = "motion")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    MoodState.DANGER -> 220
                    MoodState.CAUTION -> 600
                    MoodState.PRAISE -> 450
                    else -> 1800
                },
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phase",
    )
    if (!enabled) return Motion(1f, 0f, 0f)
    val p = phase * 2f - 1f // -1..1
    return when (state) {
        MoodState.IDLE, MoodState.OFFLINE -> Motion(1f + 0.03f * phase, 0f, 0f)
        MoodState.THINKING -> Motion(1f, 0f, -2f * phase)
        MoodState.SAFE -> Motion(1f, 0f, -3f * phase)
        MoodState.CAUTION -> Motion(1f, 2f * p, 0f)
        MoodState.DANGER -> Motion(1.05f, 3.5f * p, 0f)
        MoodState.PRAISE -> Motion(1f + 0.1f * phase, 0f, 0f)
    }
}
