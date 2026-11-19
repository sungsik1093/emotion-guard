/**
 * 홈: VPN식 큰 토글, 오늘의 요약, 남은 무료 분석 횟수, 직접 테스트 입구.
 */
package com.emotionguard.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionguard.core.designsystem.character.CharacterAvatar
import com.emotionguard.core.designsystem.component.GrowthCard
import com.emotionguard.core.designsystem.component.GuardToggle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.emotionguard.core.designsystem.component.StatCard
import com.emotionguard.core.designsystem.theme.color
import com.emotionguard.core.model.Level
import com.emotionguard.core.model.MoodState
import kotlin.math.roundToInt

@Composable
fun HomeRoute(
    onOpenTest: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    HomeScreen(
        state = state,
        resolveImage = viewModel::resolveImage,
        onToggle = {
            if (!viewModel.toggle()) context.startActivity(viewModel.accessibilitySettingsIntent())
        },
        onOpenAccessibility = { context.startActivity(viewModel.accessibilitySettingsIntent()) },
        onOpenTest = onOpenTest,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    resolveImage: (String) -> java.io.File,
    onToggle: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenTest: () -> Unit,
) {
    val active = state.guardEnabled && state.accessibilityEnabled
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(modifier = Modifier.semantics { heading() }, text = "감정 가드", fontWeight = FontWeight.Bold) })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            GuardToggle(enabled = state.guardEnabled, available = state.accessibilityEnabled, onToggle = onToggle)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                CharacterAvatar(
                    state = if (active) MoodState.SAFE else MoodState.OFFLINE,
                    skin = state.skin,
                    resolveImage = resolveImage,
                    accessory = state.growth.selectedAccessory,
                )
                Text(
                    when {
                        !state.accessibilityEnabled -> "접근성 권한을 켜야 보호할 수 있어요."
                        active -> "메신저에서 메시지를 쓰면\n제가 옆에서 봐드릴게요."
                        else -> "보호가 꺼져 있어요.\n가운데 버튼을 눌러 켜세요."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            if (!state.accessibilityEnabled) {
                Card(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("접근성 권한이 꺼져 있어요", fontWeight = FontWeight.Bold)
                        Text(
                            "설정 → 설치된 앱 → 감정 가드 → 사용 켜기. 앱을 업데이트하면 시스템이 끌 수도 있어요.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Button(onClick = onOpenAccessibility, modifier = Modifier.padding(top = 8.dp)) {
                            Text("접근성 설정 열기")
                        }
                    }
                }
            }

            GrowthCard(state.growth, Modifier.fillMaxWidth().padding(top = 16.dp))

            Text(
                "오늘",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp).semantics { heading() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("보낸 메시지", "${state.today.total}", Modifier.weight(1f))
                StatCard(
                    "평균 온도",
                    if (state.today.total == 0) "-" else "${state.today.averageScore.roundToInt()}°",
                    Modifier.weight(1f),
                    valueColor = Level.of(state.today.averageScore.roundToInt()).color(),
                )
                StatCard("바꿔 보냄", "${state.today.replacedCount}", Modifier.weight(1f))
            }

            state.quota?.let { quota ->
                Card(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(
                        Modifier.padding(16.dp).semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("오늘 남은 AI 분석", modifier = Modifier.weight(1f))
                        Text("${quota.remaining} / ${quota.limit}", fontWeight = FontWeight.Bold)
                    }
                }
            }

            OutlinedButton(onClick = onOpenTest, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("여기서 바로 테스트해보기")
            }
            Text(
                "팁: 상단바를 내려 연필 아이콘 → '감정 가드' 타일을 추가하면 VPN처럼 바로 켜고 끌 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}
