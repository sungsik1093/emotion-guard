/**
 * 레벨업으로 여는 캐릭터 장식 (디자인 문서 §15.3). 이미지 없이 Canvas로 그린다.
 * 기본 캐릭터의 눈 위치에 맞춰 그렸고, 사진 스킨 위에도 얹을 수 있다.
 */
package com.emotionguard.core.designsystem.character

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.emotionguard.core.model.Accessory
import kotlin.math.cos
import kotlin.math.sin

private val Ink = Color(0xFF2B2B33)
private val Gold = Color(0xFFF2B705)
private val GoldDark = Color(0xFFC98E00)

@Composable
fun AccessoryLayer(accessory: Accessory, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center
        when (accessory) {
            Accessory.NONE -> Unit
            Accessory.FLOWER -> drawFlower(Offset(c.x + r * 0.62f, c.y - r * 0.6f), r * 0.2f)
            Accessory.GLASSES -> drawGlasses(c, r)
            Accessory.HEADPHONES -> drawHeadphones(c, r)
            Accessory.PARTY_HAT -> drawPartyHat(c, r)
            Accessory.CROWN -> drawCrown(c, r)
        }
    }
}

private fun DrawScope.drawFlower(center: Offset, petal: Float) {
    for (i in 0 until 5) {
        val a = Math.toRadians(i * 72.0 - 90).toFloat()
        drawCircle(Color(0xFFFF8FB1), petal * 0.62f, Offset(center.x + cos(a) * petal * 0.7f, center.y + sin(a) * petal * 0.7f))
    }
    drawCircle(Color(0xFFFFD43B), petal * 0.45f, center)
}

private fun DrawScope.drawGlasses(c: Offset, r: Float) {
    val eyeY = c.y - r * 0.12f
    val dx = r * 0.34f
    val lens = r * 0.2f
    val stroke = Stroke(width = r * 0.06f)
    drawCircle(Color.White.copy(alpha = 0.25f), lens, Offset(c.x - dx, eyeY))
    drawCircle(Color.White.copy(alpha = 0.25f), lens, Offset(c.x + dx, eyeY))
    drawCircle(Ink, lens, Offset(c.x - dx, eyeY), style = stroke)
    drawCircle(Ink, lens, Offset(c.x + dx, eyeY), style = stroke)
    drawLine(Ink, Offset(c.x - dx + lens, eyeY), Offset(c.x + dx - lens, eyeY), r * 0.05f, StrokeCap.Round)
}

private fun DrawScope.drawHeadphones(c: Offset, r: Float) {
    val band = Color(0xFF5B5BD6)
    drawArc(
        color = band,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(c.x - r * 0.95f, c.y - r * 0.98f),
        size = Size(r * 1.9f, r * 1.6f),
        style = Stroke(width = r * 0.1f, cap = StrokeCap.Round),
    )
    val cup = Size(r * 0.26f, r * 0.42f)
    for (x in listOf(c.x - r * 0.98f, c.x + r * 0.72f)) {
        drawRoundRect(band, Offset(x, c.y - r * 0.28f), cup, CornerRadius(r * 0.12f))
    }
}

private fun DrawScope.drawPartyHat(c: Offset, r: Float) {
    val base = c.y - r * 0.6f
    rotate(degrees = 14f, pivot = Offset(c.x, base)) {
        val hat = Path().apply {
            moveTo(c.x - r * 0.3f, base)
            lineTo(c.x + r * 0.3f, base)
            lineTo(c.x, base - r * 0.42f)
            close()
        }
        drawPath(hat, Color(0xFF4DABF7))
        // 줄무늬
        drawLine(Color(0xFFFFD43B), Offset(c.x - r * 0.2f, base - r * 0.12f), Offset(c.x + r * 0.2f, base - r * 0.12f), r * 0.05f)
        drawLine(Color(0xFFFF8FB1), Offset(c.x - r * 0.11f, base - r * 0.26f), Offset(c.x + r * 0.11f, base - r * 0.26f), r * 0.05f)
        drawCircle(Color(0xFFFF6B6B), r * 0.07f, Offset(c.x, base - r * 0.44f))
    }
}

private fun DrawScope.drawCrown(c: Offset, r: Float) {
    val base = c.y - r * 0.6f
    val w = r * 0.4f
    val crown = Path().apply {
        moveTo(c.x - w, base)
        lineTo(c.x - w, base - r * 0.26f)
        lineTo(c.x - w * 0.5f, base - r * 0.12f)
        lineTo(c.x, base - r * 0.36f)
        lineTo(c.x + w * 0.5f, base - r * 0.12f)
        lineTo(c.x + w, base - r * 0.26f)
        lineTo(c.x + w, base)
        close()
    }
    drawPath(crown, Gold)
    drawRect(GoldDark, topLeft = Offset(c.x - w, base - r * 0.05f), size = Size(w * 2, r * 0.05f))
    drawCircle(Color(0xFFE64980), r * 0.05f, Offset(c.x, base - r * 0.16f))
}
