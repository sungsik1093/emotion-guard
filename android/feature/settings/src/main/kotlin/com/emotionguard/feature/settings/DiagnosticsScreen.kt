/**
 * 진단 화면 (디자인 문서 §5.3).
 * 메신저가 업데이트돼서 감지가 깨졌을 때, 어디서 막혔는지 바로 확인한다.
 * 메신저를 열고 입력해 본 다음 이 화면으로 돌아오면 최신 상태가 보인다.
 */
package com.emotionguard.feature.settings

import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.semantics.heading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionguard.core.model.Speaker
import com.emotionguard.core.model.SupportedMessengers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val d by viewModel.diagnostics.collectAsStateWithLifecycle()
    val a11y = viewModel.isAccessibilityEnabled()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(modifier = Modifier.semantics { heading() }, text = "진단") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(
                "메신저를 열고 메시지를 입력해 본 뒤 이 화면으로 돌아오세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Item("접근성 서비스", if (a11y) "켜짐 ✅" else "꺼짐 ❌")
            Item("마지막 메신저", d.packageName?.let { SupportedMessengers.find(it)?.displayName ?: it } ?: "-")
            Item("입력창 감지", if (d.inputFieldFound) "성공 ✅" else "아직 없음")
            Item("입력창 뷰 ID", d.inputFieldId ?: "-", mono = true)
            Item("전송 버튼 감지", if (d.sendButtonFound) "성공 ✅" else "아직 없음 (보내면 확인돼요)")
            Item("대화 맥락", "${d.contextLines.size}줄 · 신뢰도 ${(d.contextConfidence * 100).toInt()}%")
            d.contextLines.forEach {
                Text(
                    "${if (it.speaker == Speaker.ME) "나" else "상대"}: ${it.text}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                )
            }
            Item("마지막 분석", d.lastSource?.let { "${it.name} · ${d.lastLatencyMillis ?: "-"}ms" } ?: "-")
            Item("마지막 오류", d.lastError ?: "없음")
            Item(
                "갱신 시각",
                if (d.updatedAt == 0L) "-" else SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(d.updatedAt)),
            )
        }
    }
}

@Composable
private fun Item(label: String, value: String, mono: Boolean = false) {
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.4f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else null,
            modifier = Modifier.weight(0.6f),
        )
    }
}
