/**
 * 캐릭터 꾸미기 (디자인 문서 §6.4).
 *
 * - 상태 칩을 누르면 미리보기 표정이 바뀐다.
 * - 모드: 기본 캐릭터 / 사진 한 장 / 상태별 사진
 * - 사진은 Android Photo Picker로 고른다 (저장소 권한 필요 없음).
 */
package com.emotionguard.feature.character

import androidx.compose.ui.semantics.heading

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionguard.core.designsystem.character.CharacterAvatar
import com.emotionguard.core.designsystem.component.GrowthCard
import com.emotionguard.core.designsystem.component.SectionTitle
import com.emotionguard.core.model.Accessory
import com.emotionguard.core.model.Growth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.emotionguard.core.model.CharacterSkin
import com.emotionguard.core.model.MoodState
import com.emotionguard.core.model.SkinMode
import java.io.File

/** 사진 선택기에서 돌아왔을 때 어느 슬롯에 넣을지. "single"은 사진 한 장 슬롯 */
private const val SINGLE_SLOT = "single"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterRoute(viewModel: CharacterViewModel = hiltViewModel()) {
    val skin by viewModel.skin.collectAsStateWithLifecycle()
    val growth by viewModel.growth.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val slot = target
        target = null
        if (uri != null && slot != null) {
            viewModel.setImage(if (slot == SINGLE_SLOT) null else MoodState.valueOf(slot), uri)
        }
    }
    fun pick(slot: String) {
        target = slot
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(modifier = Modifier.semantics { heading() }, text = "캐릭터 꾸미기", fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        CharacterScreen(
            skin = skin,
            growth = growth,
            onAccessory = viewModel::selectAccessory,
            resolveImage = viewModel::resolveImage,
            onMode = viewModel::setMode,
            onPickSingle = { pick(SINGLE_SLOT) },
            onClearSingle = viewModel::clearSingleImage,
            onPickState = { pick(it.name) },
            onResetState = viewModel::resetState,
            onShowRing = viewModel::setShowRing,
            onAnimate = viewModel::setAnimate,
            onResetAll = { confirmReset = true },
            modifier = Modifier.padding(padding),
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(modifier = Modifier.semantics { heading() }, text = "기본 캐릭터로 되돌릴까요?") },
            text = { Text("넣어둔 사진이 모두 지워져요.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; viewModel.resetAll() }) { Text("되돌리기") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("취소") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CharacterScreen(
    skin: CharacterSkin,
    growth: Growth,
    onAccessory: (Accessory) -> Unit,
    resolveImage: (String) -> File,
    onMode: (SkinMode) -> Unit,
    onPickSingle: () -> Unit,
    onClearSingle: () -> Unit,
    onPickState: (MoodState) -> Unit,
    onResetState: (MoodState) -> Unit,
    onShowRing: (Boolean) -> Unit,
    onAnimate: (Boolean) -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var preview by rememberSaveable { mutableStateOf(MoodState.SAFE) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        // 미리보기
        Card(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CharacterAvatar(preview, skin, resolveImage, size = 120.dp, accessory = growth.selectedAccessory)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    MoodState.entries.forEach { state ->
                        FilterChip(
                            selected = preview == state,
                            onClick = { preview = state },
                            label = { Text(state.label) },
                        )
                    }
                }
            }
        }

        GrowthCard(growth, Modifier.fillMaxWidth().padding(top = 12.dp))
        Text(
            "안전한 말 +2, 대안으로 바꿔 보내기 +15, 위험한 말 없이 하루를 넘기면 연속 보너스. 하루 최대 60 XP.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        SectionTitle("장식", Modifier.padding(top = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Accessory.entries.forEach { acc ->
                val unlocked = growth.isUnlocked(acc)
                FilterChip(
                    selected = growth.selectedAccessory == acc,
                    enabled = unlocked,
                    onClick = { onAccessory(acc) },
                    label = { Text(if (unlocked) acc.label else "${acc.label} · Lv ${acc.unlockLevel}") },
                    modifier = Modifier.semantics {
                        if (!unlocked) stateDescription = "잠김, 레벨 ${acc.unlockLevel}에 열려요"
                    },
                )
            }
        }

        SectionTitle("캐릭터 종류", Modifier.padding(top = 16.dp))
        val modes = listOf(SkinMode.DEFAULT to "기본", SkinMode.SINGLE_IMAGE to "사진 한 장", SkinMode.PER_STATE to "상태별 사진")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = skin.mode == mode,
                    onClick = { onMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                ) { Text(label) }
            }
        }

        when (skin.mode) {
            SkinMode.DEFAULT -> Text(
                "앱에 들어 있는 기본 캐릭터를 써요. 상태에 따라 표정이 자연스럽게 바뀌어요.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )

            SkinMode.SINGLE_IMAGE -> SlotRow(
                title = "내 사진",
                subtitle = "모든 상태에서 이 사진을 써요. 테두리 색과 움직임으로 상태를 보여줘요.",
                preview = { CharacterAvatar(MoodState.IDLE, skin, resolveImage, size = 48.dp) },
                hasImage = skin.singleImage != null,
                onPick = onPickSingle,
                onReset = onClearSingle,
            )

            SkinMode.PER_STATE -> {
                SlotRow(
                    title = "기본 사진",
                    subtitle = "따로 정하지 않은 상태에 쓰여요. 없으면 기본 캐릭터가 나와요.",
                    preview = {
                        CharacterAvatar(MoodState.IDLE, skin.copy(mode = SkinMode.SINGLE_IMAGE), resolveImage, size = 48.dp)
                    },
                    hasImage = skin.singleImage != null,
                    onPick = onPickSingle,
                    onReset = onClearSingle,
                )
                MoodState.entries.forEach { state ->
                    SlotRow(
                        title = state.label,
                        subtitle = if (state in skin.stateImages) "내 사진" else "기본값 사용 중",
                        preview = { CharacterAvatar(state, skin, resolveImage, size = 48.dp) },
                        hasImage = state in skin.stateImages,
                        onPick = { onPickState(state) },
                        onReset = { onResetState(state) },
                    )
                }
            }
        }

        SectionTitle("효과", Modifier.padding(top = 16.dp))
        SwitchRow("상태 색 테두리", "사진을 써도 지금 상태를 색으로 알 수 있어요", skin.showColorRing, onShowRing)
        SwitchRow("움직임", "숨쉬기, 흔들림, 두근거림 효과", skin.animate, onAnimate)

        OutlinedButton(onClick = onResetAll, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
            Text("전체 초기화")
        }
    }
}

@Composable
private fun SlotRow(
    title: String,
    subtitle: String,
    preview: @Composable () -> Unit,
    hasImage: Boolean,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clearAndSetSemantics { }) { preview() }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp).semantics(mergeDescendants = true) { }) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (hasImage) TextButton(onClick = onReset, modifier = Modifier.semantics { contentDescription = "$title 기본값으로" }) { Text("기본값") }
        TextButton(onClick = onPick, modifier = Modifier.semantics { contentDescription = "$title 사진 선택" }) { Text("사진 선택") }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
