/**
 * 직접 테스트: 메신저 없이 앱 안에서 문장을 분석해 본다.
 * "상대가 보낸 메시지"를 같이 넣으면 대화 맥락에 따라 점수가 달라지는 걸 시연할 수 있다.
 */
package com.emotionguard.feature.home

import androidx.compose.ui.semantics.heading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emotionguard.core.analysis.AnalysisRepository
import com.emotionguard.core.datastore.SkinRepository
import com.emotionguard.core.designsystem.character.CharacterAvatar
import com.emotionguard.core.designsystem.component.ScoreBar
import com.emotionguard.core.model.AnalysisError
import com.emotionguard.core.model.AnalysisOutcome
import com.emotionguard.core.model.AnalysisResult
import com.emotionguard.core.model.AnalysisSource
import com.emotionguard.core.model.Category
import com.emotionguard.core.model.CharacterSkin
import com.emotionguard.core.model.ChatContext
import com.emotionguard.core.model.ChatLine
import com.emotionguard.core.model.MoodState
import com.emotionguard.core.model.Relationship
import com.emotionguard.core.model.Speaker
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.FilterChip
import androidx.compose.ui.semantics.semantics
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object TestDestination

data class TestUiState(
    val text: String = "",
    val otherMessage: String = "",
    val relationship: Relationship = Relationship.UNKNOWN,
    val loading: Boolean = false,
    val result: AnalysisResult? = null,
    val error: AnalysisError? = null,
) {
    val mood: MoodState
        get() = when {
            loading -> MoodState.THINKING
            error != null -> MoodState.OFFLINE
            result != null -> MoodState.of(result.level)
            else -> MoodState.IDLE
        }
}

@HiltViewModel
class TestViewModel @Inject constructor(
    private val analysis: AnalysisRepository,
    private val skins: SkinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TestUiState())
    val state: StateFlow<TestUiState> = _state.asStateFlow()
    val skin: StateFlow<CharacterSkin> = skins.skin.stateIn(viewModelScope, SharingStarted.Eagerly, CharacterSkin())

    fun resolveImage(name: String): File = skins.fileOf(name)

    fun onText(value: String) = _state.update { it.copy(text = value) }
    fun onOther(value: String) = _state.update { it.copy(otherMessage = value) }
    fun onRelationship(value: Relationship) = _state.update { it.copy(relationship = value) }

    fun analyze() {
        val s = _state.value
        if (s.text.isBlank() || s.loading) return
        val context = if (s.otherMessage.isBlank()) {
            ChatContext.EMPTY
        } else {
            ChatContext(listOf(ChatLine(Speaker.OTHER, s.otherMessage.trim())), confidence = 1f)
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val outcome = analysis.analyze(s.text, context, s.relationship)) {
                is AnalysisOutcome.Success -> _state.update { it.copy(loading = false, result = outcome.result) }
                is AnalysisOutcome.Failure -> _state.update { it.copy(loading = false, error = outcome.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestRoute(onBack: () -> Unit, viewModel: TestViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val skin by viewModel.skin.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(modifier = Modifier.semantics { heading() }, text = "직접 테스트") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            OutlinedTextField(
                value = state.otherMessage,
                onValueChange = viewModel::onOther,
                label = { Text("상대가 보낸 메시지 (선택)") },
                placeholder = { Text("예: 오늘 회사에서 너무 힘들었어") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onText,
                label = { Text("내가 보낼 메시지") },
                placeholder = { Text("예: 그래서 뭐 어쩌라고") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                minLines = 2,
            )
            Text(
                "대화 상대와의 관계",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Relationship.entries.forEach { r ->
                    FilterChip(
                        selected = state.relationship == r,
                        onClick = { viewModel.onRelationship(r) },
                        label = { Text(r.label) },
                    )
                }
            }
            Button(
                onClick = viewModel::analyze,
                enabled = state.text.isNotBlank() && !state.loading,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(if (state.loading) "분석 중…" else "분석하기") }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp)) {
                CharacterAvatar(state.mood, skin, viewModel::resolveImage)
                Text(
                    state.mood.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }

            state.result?.let { result ->
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        ScoreBar(result.score)
                        if (result.category != Category.NONE) {
                            Text("유형: ${result.category.label}", modifier = Modifier.padding(top = 8.dp))
                        }
                        Text(result.reason, modifier = Modifier.padding(top = 8.dp))
                        if (result.hasAlternative) {
                            Text(
                                "이렇게 말해보는 건 어때요?",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            result.alternatives.forEach { alt ->
                                Column(
                                    Modifier
                                        .padding(top = 6.dp)
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                        .semantics(mergeDescendants = true) { },
                                ) {
                                    Text(alt.tone.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(alt.text)
                                }
                            }
                        }
                        Text(
                            when (result.source) {
                                AnalysisSource.LOCAL -> "휴대폰 안에서 판단했어요 (서버 전송 없음)"
                                AnalysisSource.CACHE -> "최근 분석 결과를 다시 썼어요"
                                AnalysisSource.REMOTE -> "AI가 분석했어요"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
