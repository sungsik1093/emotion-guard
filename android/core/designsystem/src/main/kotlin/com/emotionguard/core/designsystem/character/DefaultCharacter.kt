/**
 * 기본 캐릭터: 이미지 파일 없이 Canvas로 그린 동그란 얼굴 (디자인 문서 §6.3).
 *
 * 표정은 몇 개의 숫자(입 곡률, 눈 크기, 눈썹 기울기)로 정해지고,
 * 상태가 바뀌면 그 숫자들이 부드럽게 바뀌어서 표정이 자연스럽게 변한다.
 */
package com.emotionguard.core.designsystem.character

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import com.emotionguard.core.designsystem.theme.color
import com.emotionguard.core.model.MoodState

/** 상태별 표정 파라미터 */
private data class Face(
    /** -1(울상) ~ 1(활짝 웃음) */
    val mouthCurve: Float,
    /** 입 너비 비율 */
    val mouthWidth: Float,
    /** 0(감음) ~ 1(뜸) */
    val eyeOpen: Float,
    /** 눈 크기 배율 */
    val eyeScale: Float,
    /** 눈썹 기울기: +면 걱정(안쪽이 올라감), -면 화남 */
    val browTilt: Float,
    /** 눈썹 보이기 0~1 */
    val browAlpha: Float,
    /** 눈동자 위치 (생각 중이면 위로) */
    val lookUp: Float,
)

private fun faceOf(state: MoodState): Face = when (state) {
    MoodState.IDLE -> Face(0.25f, 0.30f, 1f, 1f, 0f, 0f, 0f)
    MoodState.THINKING -> Face(0f, 0.18f, 1f, 1f, 0.15f, 0.6f, 1f)
    MoodState.SAFE -> Face(0.9f, 0.42f, 1f, 1f, 0f, 0f, 0f)
    MoodState.CAUTION -> Face(-0.3f, 0.30f, 1f, 1f, 0.6f, 1f, 0f)
    MoodState.DANGER -> Face(-0.9f, 0.38f, 1f, 1.35f, 1f, 1f, 0f)
    MoodState.PRAISE -> Face(1f, 0.48f, 1f, 1f, 0f, 0f, 0f)
    MoodState.OFFLINE -> Face(0f, 0.22f, 0f, 1f, 0f, 0f, 0f)
}

@Composable
fun DefaultCharacter(state: MoodState, modifier: Modifier = Modifier) {
    val target = faceOf(state)
    val spec = tween<Float>(durationMillis = 350)
    val mouthCurve by animateFloatAsState(target.mouthCurve, spec, label = "mouth")
    val mouthWidth by animateFloatAsState(target.mouthWidth, spec, label = "mouthWidth")
    val eyeOpen by animateFloatAsState(target.eyeOpen, spec, label = "eyeOpen")
    val eyeScale by animateFloatAsState(target.eyeScale, spec, label = "eyeScale")
    val browTilt by animateFloatAsState(target.browTilt, spec, label = "brow")
    val browAlpha by animateFloatAsState(target.browAlpha, spec, label = "browAlpha")
    val lookUp by animateFloatAsState(target.lookUp, spec, label = "look")

    val tint = state.color()
    val faceColor = lerp(Color(0xFFFFF4D6), tint, 0.18f)
    val ink = Color(0xFF2B2B33)

    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(faceColor, r)

        val eyeY = c.y - r * 0.12f - lookUp * r * 0.08f
        val eyeDx = r * 0.34f
        val eyeR = r * 0.09f * eyeScale

        // 눈
        if (state == MoodState.PRAISE) {
            drawHeart(Offset(c.x - eyeDx, eyeY), r * 0.17f, BrandHeart)
            drawHeart(Offset(c.x + eyeDx, eyeY), r * 0.17f, BrandHeart)
        } else {
            for (sign in listOf(-1f, 1f)) {
                val eye = Offset(c.x + sign * eyeDx, eyeY)
                if (eyeOpen > 0.15f) {
                    drawOval(
                        ink,
                        topLeft = Offset(eye.x - eyeR, eye.y - eyeR * eyeOpen),
                        size = Size(eyeR * 2, eyeR * 2 * eyeOpen),
                    )
                } else {
                    drawLine(ink, Offset(eye.x - eyeR * 1.3f, eye.y), Offset(eye.x + eyeR * 1.3f, eye.y), r * 0.05f, StrokeCap.Round)
                }
            }
        }

        // 눈썹
        if (browAlpha > 0.01f) {
            val browY = eyeY - r * 0.24f
            val half = r * 0.14f
            val lift = browTilt * r * 0.08f
            for (sign in listOf(-1f, 1f)) {
                val cx = c.x + sign * eyeDx
                val inner = Offset(cx - sign * half, browY - lift)
                val outer = Offset(cx + sign * half, browY + lift)
                drawLine(ink.copy(alpha = browAlpha), inner, outer, r * 0.05f, StrokeCap.Round)
            }
        }

        // 볼
        if (state == MoodState.SAFE || state == MoodState.PRAISE) {
            drawCircle(BrandHeart.copy(alpha = 0.25f), r * 0.1f, Offset(c.x - r * 0.52f, c.y + r * 0.15f))
            drawCircle(BrandHeart.copy(alpha = 0.25f), r * 0.1f, Offset(c.x + r * 0.52f, c.y + r * 0.15f))
        }

        // 입: 2차 베지어 곡선. curve가 +면 아래로 휘어 웃는 입
        val mouthY = c.y + r * 0.32f
        val halfW = r * mouthWidth
        val path = Path().apply {
            moveTo(c.x - halfW, mouthY)
            quadraticTo(c.x, mouthY + mouthCurve * r * 0.32f, c.x + halfW, mouthY)
        }
        drawPath(path, ink, style = Stroke(width = r * 0.07f, cap = StrokeCap.Round))

        // 위험할 때 땀방울
        if (state == MoodState.DANGER) {
            val drop = Path().apply {
                val x = c.x + r * 0.62f
                val y = c.y - r * 0.42f
                moveTo(x, y - r * 0.14f)
                quadraticTo(x + r * 0.11f, y + r * 0.02f, x, y + r * 0.06f)
                quadraticTo(x - r * 0.11f, y + r * 0.02f, x, y - r * 0.14f)
            }
            drawPath(drop, Color(0xFF74C0FC))
        }

        // 오프라인이면 z
        if (state == MoodState.OFFLINE) {
            val x = c.x + r * 0.45f
            val y = c.y - r * 0.6f
            val s = r * 0.16f
            val z = Path().apply {
                moveTo(x, y); lineTo(x + s, y); lineTo(x, y + s); lineTo(x + s, y + s)
            }
            drawPath(z, ink, style = Stroke(width = r * 0.04f, cap = StrokeCap.Round))
        }
    }
}

private val BrandHeart = Color(0xFFE64980)

private fun DrawScope.drawHeart(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        val x = center.x
        val y = center.y
        moveTo(x, y + size * 0.6f)
        cubicTo(x - size * 1.2f, y - size * 0.1f, x - size * 0.5f, y - size * 1.0f, x, y - size * 0.35f)
        cubicTo(x + size * 0.5f, y - size * 1.0f, x + size * 1.2f, y - size * 0.1f, x, y + size * 0.6f)
        close()
    }
    drawPath(path, color)
}
