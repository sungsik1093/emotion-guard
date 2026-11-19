/**
 * 설정: 경고 기준, 표시 방식, 개인정보, 메신저별 켜기/끄기, 진단, 기록 삭제.
 */
package com.emotionguard.feature.settings

import androidx.compose.ui.semantics.heading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.emotionguard.core.model.Relationship
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionguard.core.designsystem.component.SectionTitle
import com.emotionguard.core.designsystem.theme.color
import com.emotionguard.core.model.Level
import com.emotionguard.core.model.SupportedMessengers
import com.emotionguard.core.model.UserSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onOpenDiagnostics: () -> Unit,
    onSendReportNow: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val roomCount by viewModel.roomCount.collectAsStateWithLifecycle()
    var reportSent by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(modifier = Modifier.semantics { heading() }, text = "설정", fontWeight = FontWeight.Bold) })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            SectionTitle("경고")
            ThresholdSlider(s.warnThreshold, viewModel::setThreshold)
            SwitchRow("위험하면 카드 자동으로 펼치기", "끄면 캐릭터 표정과 진동으로만 알려요", s.autoExpandOnDanger, viewModel::setAutoExpand)
            SwitchRow("메신저 밖에서도 캐릭터 유지", "끄면 메신저를 벗어날 때 캐릭터가 사라져요", s.alwaysShowBubble, viewModel::setAlwaysShow)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("대화 상대와의 관계")
            Text(
                "관계를 정하지 않은 대화방에 쓸 기본값이에요. 대화방마다 경고 카드에서 따로 정할 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Relationship.entries.forEach { r ->
                    FilterChip(
                        selected = s.defaultRelationship == r,
                        onClick = { viewModel.setDefaultRelationship(r) },
                        label = { Text(r.label) },
                    )
                }
            }
            if (roomCount > 0) {
                ClickRow("대화방별 관계 초기화", "따로 정해둔 대화방 ${roomCount}곳") { viewModel.clearRooms() }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("감지할 메신저")
            SupportedMessengers.all.forEach { m ->
                SwitchRow(m.displayName, null, s.isMessengerEnabled(m.packageName)) { viewModel.setMessenger(m.packageName, it) }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("기록과 개인정보")
            SwitchRow("기록에 원문 저장", "이 휴대폰에만 저장돼요. 기본은 꺼져 있어요", s.saveOriginalText, viewModel::setSaveText)
            SwitchRow("'전송 추정' 기록도 통계에 포함", "입력창을 한 번에 지운 경우도 보낸 것으로 셀 수 있어요", s.countLikelySends, viewModel::setCountLikely)
            SwitchRow("주간 리포트 알림", "매주 일요일 저녁에 한 주의 감정 온도를 알려드려요", s.weeklyReportEnabled, viewModel::setWeeklyReport)
            ClickRow(
                "지금 리포트 받아보기",
                if (reportSent) "알림을 보냈어요. 알림창을 확인해 보세요." else "이번 주 리포트를 바로 알림으로 받아요",
            ) {
                onSendReportNow()
                reportSent = true
            }
            ClickRow("개인정보 안내") { showPrivacy = true }
            ClickRow("기록 전체 삭제", danger = true) { confirmClear = true }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("문제 해결")
            ClickRow("진단", "감지가 안 될 때 원인을 확인해요", onClick = onOpenDiagnostics)
            Text(
                "감정 가드 v2.0.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(modifier = Modifier.semantics { heading() }, text = "기록을 모두 삭제할까요?") },
            text = { Text("삭제한 기록은 되돌릴 수 없어요.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clearHistory() }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
        )
    }
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text(modifier = Modifier.semantics { heading() }, text = "개인정보 안내") },
            text = { Text(PRIVACY_TEXT) },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("확인") } },
        )
    }
}

@Composable
private fun ThresholdSlider(value: Int, onChange: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val shown = local.roundToInt()
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text("경고 기준 점수", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${shown}점 이상", fontWeight = FontWeight.Bold, color = Level.of(shown).color())
        }
        Slider(
            modifier = Modifier.semantics { contentDescription = "경고 기준 점수" },
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.roundToInt()) },
            valueRange = UserSettings.THRESHOLD_RANGE.first.toFloat()..UserSettings.THRESHOLD_RANGE.last.toFloat(),
        )
        Text(
            "낮출수록 더 자주 알려줘요. 기본값은 61점(위험 구간 시작)이에요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    // 행 전체를 하나의 스위치로: TalkBack이 "제목, 설명, 스위치, 켜짐"으로 한 번에 읽는다
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ClickRow(title: String, subtitle: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val PRIVACY_TEXT =
    "• 감정 가드는 지원하는 메신저에서 입력 중인 문장과 화면의 최근 대화(최대 8줄)를 읽습니다.\n" +
        "• 짧고 무난한 문장은 휴대폰 안에서만 판단하고 외부로 보내지 않습니다.\n" +
        "• 분석이 필요한 문장은 전화번호·이메일·주민번호·카드·계좌번호를 가린 뒤 감정 가드 서버를 거쳐 Anthropic Claude API로 전송됩니다.\n" +
        "• 서버는 메시지 내용을 저장하거나 기록하지 않습니다.\n" +
        "• 휴대폰에는 점수와 유형만 저장하며, 원문은 설정에서 켠 경우에만 저장합니다.\n" +
        "• 비밀번호 입력란은 읽지 않습니다."
