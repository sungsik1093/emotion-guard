/**
 * 온보딩 3장 (디자인 문서 §4.1)
 *   1. 무엇을 하는 앱인지
 *   2. 어떤 데이터가 어디로 가는지 (명시적 고지 + 동의) — Play 정책 요구사항
 *   3. 접근성 권한 켜기 안내 (+ 알림 권한)
 */
package com.emotionguard.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionguard.core.designsystem.character.DefaultCharacter
import com.emotionguard.core.model.MoodState
import kotlinx.coroutines.launch

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val accessibilityOn by viewModel.accessibilityEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    OnboardingScreen(
        accessibilityOn = accessibilityOn,
        onOpenAccessibility = { context.startActivity(viewModel.accessibilitySettingsIntent()) },
        onRequestNotification = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onFinish = { viewModel.finish(onFinished) },
    )
}

@Composable
fun OnboardingScreen(
    accessibilityOn: Boolean,
    onOpenAccessibility: () -> Unit,
    onRequestNotification: () -> Unit,
    onFinish: () -> Unit,
) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    var consent by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = pager.currentPage != 1 || consent,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> IntroPage()
                1 -> DisclosurePage(consent = consent, onConsentChange = { consent = it })
                else -> PermissionPage(accessibilityOn, onOpenAccessibility, onRequestNotification)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${pager.currentPage + 1} / 3", style = MaterialTheme.typography.labelLarge)
            when (pager.currentPage) {
                0 -> Button(onClick = { scope.launch { pager.animateScrollToPage(1) } }) { Text("다음") }
                1 -> Button(
                    enabled = consent,
                    onClick = { scope.launch { pager.animateScrollToPage(2) } },
                ) { Text("동의하고 계속") }
                else -> Row {
                    if (!accessibilityOn) TextButton(onClick = onFinish) { Text("나중에") }
                    Button(onClick = onFinish, enabled = accessibilityOn) { Text("시작하기") }
                }
            }
        }
    }
}

@Composable
private fun IntroPage() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DefaultCharacter(MoodState.SAFE, Modifier.size(140.dp))
        Spacer(Modifier.height(32.dp))
        Text("보내기 전에, 한 번 더", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "메신저에 글을 쓰는 동안 화면 구석의 친구가 표정으로 감정 온도를 알려줘요.\n" +
                "상처가 될 수 있는 말이면 더 좋은 표현도 제안해 드려요.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(Modifier.padding(top = 32.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(MoodState.SAFE, MoodState.CAUTION, MoodState.DANGER, MoodState.PRAISE).forEach {
                DefaultCharacter(it, Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun DisclosurePage(consent: Boolean, onConsentChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("어떤 정보를 쓰나요?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        listOf(
            "읽는 것" to "지원하는 메신저(카카오톡, Google 메시지 등)에서 입력 중인 문장과 화면의 최근 대화 최대 8줄을 읽어요. 비밀번호 칸은 읽지 않아요.",
            "보내는 곳" to "분석을 위해 문장이 감정 가드 서버를 거쳐 Anthropic의 Claude AI로 전송돼요. 서버는 내용을 저장하지 않아요.",
            "보내기 전에" to "전화번호·이메일·주민번호·카드·계좌번호는 가린 뒤 보내요. 짧고 무난한 문장은 아예 보내지 않고 휴대폰 안에서 판단해요.",
            "저장하는 것" to "보낸 메시지의 점수와 유형만 이 휴대폰에 저장해요. 원문은 설정에서 켤 때만 저장되고, 언제든 전체 삭제할 수 있어요.",
            "끄는 방법" to "홈 화면 토글, 상단바 빠른 설정, 알림의 [끄기]로 언제든 멈출 수 있어요.",
        ).forEach { (title, body) ->
            Card(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .toggleable(value = consent, role = Role.Checkbox, onValueChange = onConsentChange),
        ) {
            Checkbox(checked = consent, onCheckedChange = null)
            Text("위 내용을 확인했고 동의합니다.", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun PermissionPage(
    accessibilityOn: Boolean,
    onOpenAccessibility: () -> Unit,
    onRequestNotification: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DefaultCharacter(if (accessibilityOn) MoodState.PRAISE else MoodState.THINKING, Modifier.size(120.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            if (accessibilityOn) "준비 완료!" else "접근성 권한을 켜주세요",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (accessibilityOn) {
                "이제 메신저를 열고 메시지를 써보세요."
            } else {
                "설정 → 설치된 앱(또는 다운로드한 앱) → 감정 가드 → 사용 켜기\n처음 한 번만 하면 돼요."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
        if (!accessibilityOn) {
            Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) { Text("접근성 설정 열기") }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(onClick = onRequestNotification, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("보호 상태 알림 허용")
            }
        }
    }
}
