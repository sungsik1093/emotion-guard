/**
 * 감정 가드의 전체 흐름을 조율한다.
 *
 *   InputSource(감지) ─▶ 디바운스 1.2초 ─▶ AnalysisRepository(1차 필터·가리기·캐시·서버)
 *        │                                       │
 *        │                                       ▼
 *        │                              MoodStateMachine ─▶ 오버레이(캐릭터·카드)
 *        ▼
 *     Sent(전송) ─▶ HistoryRepository(기록)
 *
 * 보호가 꺼져 있으면(설정 guardEnabled = false) 이벤트를 받아도 아무것도 하지 않는다.
 */
package com.emotionguard.feature.guard

import com.emotionguard.core.analysis.AnalysisRepository
import com.emotionguard.core.common.ApplicationScope
import com.emotionguard.core.common.DiagnosticsBus
import com.emotionguard.core.database.HistoryRepository
import com.emotionguard.core.datastore.GrowthRepository
import com.emotionguard.core.datastore.RelationshipRepository
import com.emotionguard.core.datastore.SettingsRepository
import com.emotionguard.core.datastore.SkinRepository
import com.emotionguard.core.model.AnalysisOutcome
import com.emotionguard.core.model.AnalysisResult
import com.emotionguard.core.model.AnalysisSource
import com.emotionguard.core.model.EmotionRecord
import com.emotionguard.core.model.Level
import com.emotionguard.core.model.MoodState
import com.emotionguard.core.model.Relationship
import com.emotionguard.core.model.UserSettings
import com.emotionguard.feature.guard.input.InputEvent
import com.emotionguard.feature.guard.input.InputSource
import com.emotionguard.feature.guard.mood.MoodEvent
import com.emotionguard.feature.guard.notification.GuardNotifier
import com.emotionguard.feature.guard.mood.MoodStateMachine
import com.emotionguard.feature.guard.overlay.OverlayActions
import com.emotionguard.feature.guard.overlay.OverlayUiState
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GuardSession @Inject constructor(
    private val analysis: AnalysisRepository,
    private val history: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val skinRepository: SkinRepository,
    private val relationships: RelationshipRepository,
    private val growth: GrowthRepository,
    private val notifier: GuardNotifier,
    private val diagnostics: DiagnosticsBus,
    @ApplicationScope private val appScope: CoroutineScope,
) : OverlayActions {
    /** 오버레이 콜백 */
    interface Overlay {
        fun show(packageName: String)
        fun hide()
        fun setCardVisible(visible: Boolean)
        fun onMoodChanged(mood: MoodState)
    }

    private val _ui = MutableStateFlow(OverlayUiState())
    val ui: StateFlow<OverlayUiState> = _ui.asStateFlow()

    private lateinit var scope: CoroutineScope
    private lateinit var source: InputSource
    private lateinit var overlay: Overlay
    private lateinit var settings: StateFlow<UserSettings>

    private val pendingText = MutableStateFlow("")
    private var activePackage: String? = null
    private var praiseJob: Job? = null
    private var reanalyzeJob: Job? = null

    /** 최근 분석 결과 (전송 기록에 재사용) */
    private val recentResults = object : LinkedHashMap<String, AnalysisResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AnalysisResult>?) = size > 20
    }

    /** "대안으로 바꾸기"로 넣은 문장 */
    private var replacedText: String? = null

    fun resolveImage(name: String): File = skinRepository.fileOf(name)

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope, source: InputSource, overlay: Overlay) {
        this.scope = scope
        this.source = source
        this.overlay = overlay
        settings = settingsRepository.settings.stateIn(scope, SharingStarted.Eagerly, UserSettings())

        scope.launch {
            skinRepository.skin.collect { skin -> _ui.update { it.copy(skin = skin) } }
        }
        scope.launch {
            growth.growth.map { it.selectedAccessory }.distinctUntilChanged()
                .collect { acc -> _ui.update { it.copy(accessory = acc) } }
        }
        scope.launch {
            settings.collect { s ->
                _ui.update { it.copy(threshold = s.warnThreshold) }
                if (!s.guardEnabled) reset(hideOverlay = true)
            }
        }
        scope.launch {
            _ui.map { it.mood.state }.distinctUntilChanged().collect(overlay::onMoodChanged)
        }
        scope.launch { source.events.collect(::onInput) }
        scope.launch {
            pendingText
                .debounce(DEBOUNCE_MS)
                .map { it.trim() }
                .distinctUntilChanged() // 비웠다가 같은 글을 다시 쓰면 다시 분석하도록 filter보다 먼저
                .filter { it.length >= MIN_LENGTH }
                .collectLatest(::analyze)
        }
    }

    // ── 오버레이에서 오는 동작 ──────────────────────────────

    override fun onBubbleTap() {
        val visible = !_ui.value.cardVisible
        _ui.update { it.copy(cardVisible = visible) }
        overlay.setCardVisible(visible)
    }

    override fun onDismissCard() {
        _ui.update { it.copy(cardVisible = false) }
        overlay.setCardVisible(false)
    }

    override fun onSelectAlternative(index: Int) {
        _ui.update { it.copy(selectedAlternative = index) }
    }

    override fun onReplace() {
        val state = _ui.value
        val alternatives = state.mood.result?.alternatives.orEmpty()
        val alternative = alternatives.getOrNull(state.selectedAlternative)?.text
            ?: alternatives.firstOrNull()?.text
            ?: return
        if (source.replaceText(alternative)) {
            replacedText = alternative.trim()
            dispatch(MoodEvent.Replaced)
            onDismissCard()
            // 앱이 글을 바꿔 넣으면 "글 변경" 이벤트를 보내지 않는 메신저가 있다(Google 메시지 등).
            // 이벤트를 기다리지 않고 바뀐 문장을 직접 분석 대기열에 넣는다.
            pendingText.value = alternative
        }
    }

    /**
     * 경고 카드에서 대화 상대와의 관계를 고르면 이 방에 저장하고, 지금 문장을 그 관계로 다시 분석한다.
     * 이미 선택된 관계를 다시 누르면 "기본"으로 되돌린다.
     */
    override fun onRelationship(relationship: Relationship) {
        val next = if (_ui.value.relationship == relationship) Relationship.UNKNOWN else relationship
        val room = _ui.value.room
        _ui.update { it.copy(relationship = next) }
        reanalyzeJob?.cancel()
        reanalyzeJob = scope.launch {
            relationships.set(room, next)
            val text = pendingText.value.trim()
            if (text.length >= MIN_LENGTH) analyze(text)
        }
    }

    // ── 내부 흐름 ─────────────────────────────────────────

    private fun onInput(event: InputEvent) {
        val s = settings.value
        if (!s.guardEnabled) return

        when (event) {
            is InputEvent.Focused -> {
                if (!s.isMessengerEnabled(event.packageName)) return
                activePackage = event.packageName
                overlay.show(event.packageName)
            }

            is InputEvent.TextChanged -> {
                if (!s.isMessengerEnabled(event.packageName)) return
                if (activePackage != event.packageName) {
                    activePackage = event.packageName
                    overlay.show(event.packageName)
                }
                pendingText.value = event.text
                if (event.text.isBlank()) {
                    dispatch(MoodEvent.InputCleared)
                    onDismissCard()
                }
            }

            is InputEvent.Sent -> if (s.isMessengerEnabled(event.packageName)) record(event)

            InputEvent.Blurred -> reset(hideOverlay = !s.alwaysShowBubble)
        }
    }

    private suspend fun analyze(text: String) {
        val context = source.readContext()
        val room = source.currentRoom()
        val relationship = relationships.relationshipFor(room)
        _ui.update { it.copy(room = room, relationship = relationship) }
        diagnostics.update { it.copy(relationship = relationship) }
        val started = System.currentTimeMillis()
        // 1차 필터로 바로 끝나는 경우 "생각 중" 표정이 깜빡이지 않도록 조금 늦게 띄운다
        val thinking = scope.launch {
            delay(THINKING_DELAY_MS)
            dispatch(MoodEvent.Thinking)
        }
        val outcome = analysis.analyze(text, context, relationship)
        thinking.cancel()

        // 그사이 입력창이 비워졌으면 결과를 버린다
        if (pendingText.value.isBlank()) return

        when (outcome) {
            is AnalysisOutcome.Success -> {
                val result = outcome.result
                recentResults[result.text] = result
                _ui.update { it.copy(selectedAlternative = 0) }
                dispatch(MoodEvent.Analyzed(result))
                diagnostics.update {
                    it.copy(
                        lastLatencyMillis = System.currentTimeMillis() - started,
                        lastSource = result.source,
                        lastError = null,
                    )
                }
                val s = settings.value
                if (s.autoExpandOnDanger && result.level == Level.DANGER && !_ui.value.cardVisible) onBubbleTap()
            }

            is AnalysisOutcome.Failure -> {
                dispatch(MoodEvent.Failed(outcome.error))
                diagnostics.update { it.copy(lastError = outcome.error.message) }
            }
        }
    }

    private fun record(event: InputEvent.Sent) {
        val text = event.text.trim()
        val s = settings.value
        val replaced = replacedText != null && replacedText == text
        replacedText = null
        val cached = recentResults[text]

        appScope.launch {
            val relationship = relationships.relationshipFor(source.currentRoom())
            val result = cached ?: when (val outcome = analysis.analyze(text, source.readContext(), relationship)) {
                is AnalysisOutcome.Success -> outcome.result
                is AnalysisOutcome.Failure -> return@launch // 분석 못 한 메시지는 기록하지 않는다
            }
            history.add(
                EmotionRecord(
                    id = 0,
                    timestamp = System.currentTimeMillis(),
                    packageName = event.packageName,
                    score = result.score,
                    category = result.category,
                    // 대안으로 바꾼 메시지는 바꾸기 전에 경고를 받은 것이다 (바꿔 보낸 비율 계산에 필요)
                    warned = replaced || result.score >= s.warnThreshold,
                    replaced = replaced,
                    sendConfidence = event.confidence,
                    analyzedLocally = result.source == AnalysisSource.LOCAL,
                    originalText = if (s.saveOriginalText) text else null,
                ),
            )
            // 캐릭터 성장: 경험치를 더하고, 레벨업하면 알림 + 칭찬 표정
            val update = growth.onSent(result.level, replaced)
            if (update.leveledUp) {
                notifier.showLevelUp(update.growth.level, update.unlocked)
                scope.launch { dispatch(MoodEvent.Replaced) }
            }
        }
        dispatch(MoodEvent.InputCleared)
    }

    private fun dispatch(event: MoodEvent) {
        val before = _ui.value.mood
        val after = MoodStateMachine.reduce(before, event)
        _ui.update { it.copy(mood = after) }
        if (after.state == MoodState.PRAISE && before.state != MoodState.PRAISE) {
            praiseJob?.cancel()
            praiseJob = scope.launch {
                delay(MoodStateMachine.PRAISE_DURATION_MS)
                dispatch(MoodEvent.PraiseExpired)
            }
        }
    }

    private fun reset(hideOverlay: Boolean) {
        activePackage = null
        pendingText.value = ""
        replacedText = null
        _ui.update { it.copy(mood = MoodStateMachine.reduce(it.mood, MoodEvent.InputCleared), cardVisible = false) }
        if (hideOverlay) overlay.hide() else overlay.setCardVisible(false)
    }

    companion object {
        const val DEBOUNCE_MS = 1_200L
        const val MIN_LENGTH = 2
        const val THINKING_DELAY_MS = 150L
    }
}
