/**
 * 메신저 위에 뜨는 화면 (Compose).
 * - FloatingBubble: 표정이 바뀌는 작은 캐릭터
 * - WarningCard: 관계 선택 · 점수 · 이유 · 말투별 대안 문장 카드
 */
package com.emotionguard.feature.guard.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emotionguard.core.designsystem.character.CharacterAvatar
import com.emotionguard.core.designsystem.component.ScoreBar
import com.emotionguard.core.designsystem.theme.color
import com.emotionguard.core.model.Accessory
import com.emotionguard.core.model.CharacterSkin
import com.emotionguard.core.model.Category
import com.emotionguard.core.model.ChatRoom
import com.emotionguard.core.model.MoodState
import com.emotionguard.core.model.Relationship
import com.emotionguard.feature.guard.mood.MoodUi
import java.io.File
import kotlinx.coroutines.delay

data class OverlayUiState(
    val mood: MoodUi = MoodUi(),
    val skin: CharacterSkin = CharacterSkin(),
    val accessory: Accessory = Accessory.NONE,
    val threshold: Int = 61,
    val cardVisible: Boolean = false,
    val room: ChatRoom? = null,
    val relationship: Relationship = Relationship.UNKNOWN,
    /** 경고 카드에서 고른 대안 문장 번호 */
    val selectedAlternative: Int = 0,
)

/** 오버레이에서 사용자가 하는 동작 */
interface OverlayActions {
    fun onBubbleTap()
    fun onDismissCard()
    fun onReplace()
    fun onSelectAlternative(index: Int)
    fun onRelationship(relationship: Relationship)
}

@Composable
fun FloatingBubble(
    state: OverlayUiState,
    resolveImage: (String) -> File,
    modifier: Modifier = Modifier,
) {
    // 3초 동안 변화가 없으면 반투명하게 (대기·안전 상태일 때만)
    var dimmed by remember { mutableStateOf(false) }
    LaunchedEffect(state.mood.state, state.cardVisible) {
        dimmed = false
        delay(3_000)
        dimmed = !state.cardVisible && state.mood.state in setOf(MoodState.IDLE, MoodState.SAFE, MoodState.OFFLINE)
    }
    val alpha by animateFloatAsState(if (dimmed) 0.5f else 1f, tween(400), label = "alpha")

    Box(modifier.padding(6.dp).alpha(alpha)) {
        CharacterAvatar(
            state = state.mood.state,
            skin = state.skin,
            resolveImage = resolveImage,
            size = BUBBLE_SIZE_DP.dp,
            accessory = state.accessory,
        )
    }
}

private val SELECTABLE_RELATIONSHIPS = listOf(
    Relationship.FRIEND, Relationship.WORK, Relationship.PARTNER, Relationship.FAMILY,
)

@Composable
fun WarningCard(
    state: OverlayUiState,
    actions: OverlayActions,
    modifier: Modifier = Modifier,
) {
    val mood = state.mood
    val result = mood.result
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            RelationshipRow(state.room, state.relationship, actions::onRelationship)

            when {
                mood.state == MoodState.OFFLINE && mood.error != null -> {
                    Text("잠깐 쉬는 중이에요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        mood.error.message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                mood.state == MoodState.THINKING && result == null -> {
                    Text("문장을 살펴보는 중…", style = MaterialTheme.typography.bodyMedium)
                }

                result != null -> {
                    ScoreBar(result.score)
                    if (result.category != Category.NONE) {
                        AssistChip(
                            onClick = {},
                            label = { Text(result.category.label) },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Text(
                        result.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (result.hasAlternative) {
                        val selected = state.selectedAlternative.coerceIn(0, result.alternatives.lastIndex)
                        Text(
                            "이렇게 말해보는 건 어때요?",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        if (result.alternatives.size > 1) {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                result.alternatives.forEachIndexed { i, alt ->
                                    FilterChip(
                                        selected = i == selected,
                                        onClick = { actions.onSelectAlternative(i) },
                                        label = { Text(alt.tone.label) },
                                    )
                                }
                            }
                        }
                        Text(
                            result.alternatives[selected].text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        )
                    }
                }

                else -> Text("입력하면 감정 온도를 알려드릴게요.", style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = actions::onDismissCard) { Text("닫기") }
                if (result?.hasAlternative == true && mood.state != MoodState.PRAISE) {
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = actions::onReplace) { Text("이걸로 바꾸기") }
                }
            }
        }
    }
}

/** "대화 상대: [친구][직장][연인][가족]" — 고르면 이 대화방에 기억된다 */
@Composable
private fun RelationshipRow(room: ChatRoom?, relationship: Relationship, onSelect: (Relationship) -> Unit) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            if (room != null) "${room.title} 님과의 관계" else "대화 상대와의 관계",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SELECTABLE_RELATIONSHIPS.forEach { r ->
                FilterChip(
                    selected = relationship == r,
                    onClick = { onSelect(r) },
                    label = { Text(r.label) },
                )
            }
        }
    }
}

/** 캐릭터 옆 작은 점 (경고 기준 이상일 때 카드에 내용이 있다는 표시) */
@Composable
fun AttentionDot(state: OverlayUiState, modifier: Modifier = Modifier) {
    val score = state.mood.result?.score ?: return
    if (score < state.threshold || state.cardVisible) return
    Box(
        modifier
            .size(12.dp)
            .background(state.mood.state.color(), RoundedCornerShape(6.dp)),
    )
}

const val BUBBLE_SIZE_DP = 56
