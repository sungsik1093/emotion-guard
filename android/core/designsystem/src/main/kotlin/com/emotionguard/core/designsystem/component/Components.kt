/**
 * 여러 화면에서 쓰는 공통 컴포넌트.
 */
package com.emotionguard.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emotionguard.core.designsystem.theme.BrandColors
import com.emotionguard.core.designsystem.theme.color
import com.emotionguard.core.model.Growth
import com.emotionguard.core.model.GrowthRules
import com.emotionguard.core.model.Level
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo

/**
 * VPN 앱처럼 큰 원형 켜기/끄기 버튼.
 * 켜져 있으면 바깥 고리가 천천히 퍼지며 "보호 중"임을 보여준다.
 */
@Composable
fun GuardToggle(
    enabled: Boolean,
    available: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val active = enabled && available
    val fill by animateColorAsState(
        if (active) BrandColors.Primary else MaterialTheme.colorScheme.surfaceVariant,
        tween(400),
        label = "fill",
    )
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "ring",
    )
    val press by animateFloatAsState(if (active) 1f else 0.94f, tween(300), label = "press")
    val label = when {
        !available -> "권한 필요"
        enabled -> "보호 중"
        else -> "꺼짐"
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics(mergeDescendants = true) {
                contentDescription = "감정 가드 보호"
                stateDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Canvas(Modifier.size(size)) {
                val r = this.size.minDimension / 2f
                drawCircle(
                    BrandColors.Primary.copy(alpha = 0.35f * (1f - ring)),
                    radius = r * (0.78f + 0.22f * ring),
                    style = Stroke(width = 6.dp.toPx()),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(size * 0.72f)
                .scale(press)
                .clip(CircleShape)
                .background(fill)
                .toggleable(value = active, role = Role.Switch, onValueChange = { onToggle() }),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (active) "ON" else "OFF",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 0~100 점수를 색 막대로 보여주는 감정 온도계 */
@Composable
fun ScoreBar(score: Int, modifier: Modifier = Modifier) {
    val level = Level.of(score)
    val fraction by animateFloatAsState(score.coerceIn(0, 100) / 100f, tween(400), label = "score")
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = "감정 온도 ${score}도, ${level.label}"
            progressBarRangeInfo = ProgressBarRangeInfo(score.toFloat(), 0f..100f)
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("감정 온도", style = MaterialTheme.typography.labelMedium)
            Text(
                "$score° · ${level.label}",
                style = MaterialTheme.typography.labelMedium,
                color = level.color(),
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(level.color()),
            )
        }
    }
}

/** 제목 + 값 형태의 작은 요약 카드 */
@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Card(
        modifier = modifier.semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(vertical = 8.dp).semantics { heading() },
    )
}

/** 캐릭터 레벨과 경험치 (디자인 문서 §15.3) */
@Composable
fun GrowthCard(growth: Growth, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(growth.progress, tween(500), label = "xp")
    val next = if (growth.level >= GrowthRules.MAX_LEVEL) "최고 레벨이에요" else "다음 레벨까지 ${growth.xpToNext} XP"
    Card(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = buildString {
                append("캐릭터 레벨 ${growth.level}, $next")
                if (growth.streakDays > 0) append(", ${growth.streakDays}일 연속 기록 중")
            }
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lv ${growth.level}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    next,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                if (growth.streakDays > 0) {
                    Text(
                        "${growth.streakDays}일 연속",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandColors.Safe,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}
