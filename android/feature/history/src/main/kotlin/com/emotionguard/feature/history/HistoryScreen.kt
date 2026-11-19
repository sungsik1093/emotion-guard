/**
 * 감정 기록: 주간/월간 하루 평균 차트, 요약, 유형별 횟수, 최근 기록.
 * 차트는 라이브러리 없이 Compose Canvas로 직접 그린다.
 */
package com.emotionguard.feature.history

import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.semantics.heading

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionguard.core.designsystem.component.SectionTitle
import com.emotionguard.core.designsystem.component.StatCard
import com.emotionguard.core.designsystem.theme.color
import com.emotionguard.core.model.Category
import com.emotionguard.core.model.DailyAverage
import com.emotionguard.core.model.EmotionRecord
import com.emotionguard.core.model.Level
import com.emotionguard.core.model.SendConfidence
import com.emotionguard.core.model.SupportedMessengers
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun HistoryRoute(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state, viewModel::selectPeriod)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(state: HistoryUiState, onPeriod: (Period) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(modifier = Modifier.semantics { heading() }, text = "감정 기록", fontWeight = FontWeight.Bold) })
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Period.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = state.period == p,
                            onClick = { onPeriod(p) },
                            shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
                        ) { Text(p.label) }
                    }
                }
            }
            item {
                val s = state.summary
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        "평균 온도",
                        if (s.total == 0) "-" else "${s.averageScore.roundToInt()}°",
                        Modifier.weight(1f),
                        valueColor = Level.of(s.averageScore.roundToInt()).color(),
                    )
                    StatCard("경고", "${s.warnedCount}회", Modifier.weight(1f))
                    StatCard(
                        "바꿔 보낸 비율",
                        if (s.warnedCount == 0) "-" else "${(s.replaceRate * 100).roundToInt()}%",
                        Modifier.weight(1f),
                    )
                }
            }
            item {
                SectionTitle("하루 평균 감정 온도", Modifier.padding(top = 16.dp))
                DailyChart(state.daily, Modifier.fillMaxWidth().height(180.dp))
            }
            item {
                SectionTitle("유형별 횟수", Modifier.padding(top = 16.dp))
                CategoryBars(state.summary.byCategory)
            }
            item { SectionTitle("최근 기록", Modifier.padding(top = 16.dp)) }
            if (state.recent.isEmpty()) {
                item {
                    Text(
                        "아직 기록이 없어요. 보호를 켜고 메신저에서 메시지를 보내보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.recent, key = { it.id }) { RecordRow(it) }
        }
    }
}

@Composable
private fun DailyChart(days: List<DailyAverage>, modifier: Modifier) {
    if (days.isEmpty()) return
    val grid = MaterialTheme.colorScheme.outlineVariant
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val formatter = DateTimeFormatter.ofPattern(if (days.size <= 7) "E" else "d")
    val zone = ZoneId.systemDefault()

    // TalkBack: 막대 대신 "최근 7일 하루 평균: 화 20도, 수 기록 없음…"으로 읽는다
    val summary = remember(days) {
        val dayName = DateTimeFormatter.ofPattern("M월 d일 E")
        "최근 ${days.size}일 하루 평균 감정 온도: " + days.filter { it.count > 0 || days.size <= 7 }.joinToString(", ") { d ->
            val name = dayName.format(Instant.ofEpochMilli(d.dayStartMillis).atZone(zone))
            if (d.count == 0) "$name 기록 없음" else "$name ${d.average.roundToInt()}도"
        }.ifEmpty { "기록 없음" }
    }
    Column(modifier.clearAndSetSemantics { contentDescription = summary }) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val gap = size.width / days.size
            val barWidth = gap * 0.6f
            // 위험 기준선(61)과 주의 기준선(31)
            listOf(31f, 61f).forEach { line ->
                val y = size.height * (1f - line / 100f)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
            days.forEachIndexed { i, d ->
                val x = i * gap + (gap - barWidth) / 2
                if (d.count == 0) {
                    drawRoundRect(empty, Offset(x, size.height - 4.dp.toPx()), Size(barWidth, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                } else {
                    val h = (size.height * d.average / 100f).coerceAtLeast(4.dp.toPx())
                    drawRoundRect(
                        Level.of(d.average.roundToInt()).color(),
                        Offset(x, size.height - h),
                        Size(barWidth, h),
                        CornerRadius(4.dp.toPx()),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            days.forEachIndexed { i, d ->
                val showLabel = days.size <= 7 || i % 5 == 0 || i == days.lastIndex
                Text(
                    if (showLabel) formatter.format(Instant.ofEpochMilli(d.dayStartMillis).atZone(zone)) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CategoryBars(counts: Map<Category, Int>) {
    if (counts.isEmpty()) {
        Text("경고 유형 기록이 없어요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val max = counts.values.max().coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        counts.entries.sortedByDescending { it.value }.forEach { (category, count) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "${category.label} ${count}회" },
            ) {
                Text(category.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(88.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(count.toFloat() / max)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text("$count", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("M/d HH:mm")

@Composable
private fun RecordRow(record: EmotionRecord) {
    val level = Level.of(record.score)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(level.color().copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${record.score}",
                color = level.color(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = "${record.score}점" },
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                record.originalText ?: (if (record.category == Category.NONE) level.label else record.category.label),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            val app = SupportedMessengers.find(record.packageName)?.displayName ?: record.packageName
            val tags = buildList {
                add(app)
                if (record.replaced) add("대안으로 바꿈")
                if (record.sendConfidence == SendConfidence.LIKELY) add("전송 추정")
            }.joinToString(" · ")
            Text(tags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            timeFormat.format(Instant.ofEpochMilli(record.timestamp).atZone(ZoneId.systemDefault())),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
