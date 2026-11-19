/**
 * 접근성 이벤트를 InputEvent로 바꾸는 감지 계층 구현체.
 *
 * 지원 메신저가 아닌 앱의 이벤트는 내용을 읽지 않고 바로 버린다.
 * (다른 앱으로 넘어갔다는 사실만 확인해서 캐릭터를 숨긴다)
 */
package com.emotionguard.feature.guard.input

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.emotionguard.core.common.DiagnosticsBus
import com.emotionguard.core.model.ChatContext
import com.emotionguard.core.model.ChatRoom
import com.emotionguard.core.model.SupportedMessengers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class AccessibilityInputSource(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val diagnostics: DiagnosticsBus,
) : InputSource {

    private val _events = MutableSharedFlow<InputEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<InputEvent> = _events

    private val contextReader = ChatContextReader()
    private val sendDetector = SendDetector()
    private var pollJob: Job? = null

    private var activePackage: String? = null
    private var inputNode: AccessibilityNodeInfo? = null
    private var currentText: String = ""

    fun onEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val profile = MessengerProfiles.of(pkg)
        val supported = SupportedMessengers.find(pkg) != null

        if (!supported || profile == null) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && activePackage != null) {
                // 우리 앱의 오버레이 창도 이 이벤트를 내므로, 우리 앱은 화면(Activity)이 열릴 때만 "벗어남"으로 본다
                val ownActivity = pkg == service.packageName &&
                    event.className?.toString()?.endsWith("Activity") == true
                if (ownActivity || (pkg != service.packageName && !isSystemUi(pkg))) leave()
            }
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val node = event.source ?: return
                if (node.isEditable && !node.isPassword) focus(pkg, node, profile)
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val node = event.source ?: return
                if (!node.isEditable || node.isPassword) return
                focus(pkg, node, profile)
                onText(pkg, readText(node))
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val node = event.source ?: return
                if (isSendButton(node, profile)) {
                    diagnostics.update { it.copy(sendButtonFound = true) }
                    sendDetector.onSendClick(now())
                    schedulePoll(pkg)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 앱이 입력창을 코드로 비우면(전송 후 등) 글 변경 이벤트가 안 오는 메신저가 있다.
                // 화면이 바뀔 때 입력창을 직접 다시 읽어서 놓친 변경을 잡는다.
                syncInputText(pkg)
                if (sendDetector.hasPending) {
                    val mine = readContext().lines
                        .filter { it.speaker == com.emotionguard.core.model.Speaker.ME }
                        .map { it.text }
                    sendDetector.onMyBubbles(mine, now())
                }
            }
        }
    }

    override fun readContext(): ChatContext {
        val pkg = activePackage ?: return ChatContext.EMPTY
        val profile = MessengerProfiles.of(pkg) ?: return ChatContext.EMPTY
        val context = runCatching {
            contextReader.read(
                root = service.rootInActiveWindow,
                profile = profile,
                screenWidth = service.resources.displayMetrics.widthPixels,
                currentInput = currentText,
                screenHeight = service.resources.displayMetrics.heightPixels,
            )
        }.getOrDefault(ChatContext.EMPTY)
        diagnostics.update { it.copy(contextLines = context.lines, contextConfidence = context.confidence) }
        return context
    }

    override fun currentRoom(): ChatRoom? {
        val pkg = activePackage ?: return null
        val profile = MessengerProfiles.of(pkg) ?: return null
        val title = runCatching {
            contextReader.readRoomTitle(service.rootInActiveWindow, profile, service.resources.displayMetrics.heightPixels)
        }.getOrNull()
        diagnostics.update { it.copy(roomTitle = title) }
        return title?.let { ChatRoom(pkg, it) }
    }

    override fun replaceText(newText: String): Boolean {
        val node = inputNode?.takeIf { it.refresh() }
            ?: service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok) {
            // 글 변경 이벤트가 안 올 수도 있으니 직접 상태를 맞춘다 (전송 판단에 필요)
            currentText = newText
            sendDetector.onText(newText, now())
            // 커서를 맨 끝으로
            val selection = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newText.length)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newText.length)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
        }
        return ok
    }

    private fun focus(pkg: String, node: AccessibilityNodeInfo, profile: MessengerProfile) {
        inputNode = node
        if (activePackage != pkg) {
            activePackage = pkg
            currentText = ""
            val id = node.viewIdResourceName
            diagnostics.update {
                it.copy(
                    packageName = pkg,
                    inputFieldFound = true,
                    inputFieldId = id,
                    sendButtonFound = false,
                    contextLines = emptyList(),
                )
            }
            _events.tryEmit(InputEvent.Focused(pkg))
        }
        if (profile.fullInputIds.isNotEmpty() && node.viewIdResourceName != null &&
            node.viewIdResourceName !in profile.fullInputIds
        ) {
            // 프로필에 적힌 ID와 다르다: 메신저가 업데이트됐을 수 있음 (진단 화면에 표시됨)
            diagnostics.update { it.copy(inputFieldId = node.viewIdResourceName + " (프로필과 다름)") }
        }
    }

    private fun leave() {
        activePackage = null
        inputNode = null
        currentText = ""
        _events.tryEmit(InputEvent.Blurred)
    }

    private fun readText(node: AccessibilityNodeInfo): String =
        if (node.isShowingHintText) "" else node.text?.toString().orEmpty()

    private fun onText(pkg: String, text: String) {
        if (text == currentText) return
        currentText = text
        sendDetector.onText(text, now())
        schedulePoll(pkg)
        _events.tryEmit(InputEvent.TextChanged(pkg, text))
    }

    private var lastSyncAt = 0L

    private fun syncInputText(pkg: String) {
        if (currentText.isEmpty()) return
        val t = now()
        if (t - lastSyncAt < SYNC_INTERVAL_MS) return
        lastSyncAt = t
        val node = inputNode?.takeIf { it.refresh() } ?: return
        onText(pkg, readText(node))
    }

    /**
     * 실제로 눌린 뷰는 버튼 안쪽의 아이콘인 경우가 많아서 부모 몇 단계까지 같이 본다.
     * 라벨은 "SMS 보내기"처럼 앞에 다른 말이 붙기도 해서 포함 여부로 비교한다.
     */
    private fun isSendButton(node: AccessibilityNodeInfo, profile: MessengerProfile): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(SEND_BUTTON_PARENT_DEPTH) {
            val n = current ?: return false
            val id = n.viewIdResourceName
            if (id != null && id in profile.fullSendIds) return true
            val label = (n.contentDescription ?: n.text)?.toString()?.trim()?.lowercase()
            if (label != null && label.length <= MAX_SEND_LABEL_LENGTH &&
                profile.sendButtonLabels.any { label.contains(it) }
            ) {
                return true
            }
            current = n.parent
        }
        return false
    }

    /** 전송 판단 창이 끝날 때 결과를 확인한다. */
    private fun schedulePoll(pkg: String) {
        if (!sendDetector.hasPending) return
        pollJob?.cancel()
        pollJob = scope.launch {
            delay(SEND_WINDOW_MS + 50)
            // 판단 직전에 입력창을 한 번 더 읽는다 (비워진 이벤트를 놓쳤을 수 있음)
            inputNode?.takeIf { it.refresh() }?.let { node ->
                val text = readText(node)
                if (text != currentText) {
                    currentText = text
                    sendDetector.onText(text, now())
                    _events.tryEmit(InputEvent.TextChanged(pkg, text))
                }
            }
            sendDetector.poll(now())?.let { decision ->
                _events.tryEmit(InputEvent.Sent(pkg, decision.text, decision.confidence))
            }
        }
    }

    private fun isSystemUi(pkg: String) =
        pkg == "com.android.systemui" || pkg.contains("inputmethod") || pkg.contains("keyboard") ||
            pkg == "com.samsung.android.honeyboard"

    private fun now() = SystemClock.uptimeMillis()

    companion object {
        const val SEND_WINDOW_MS = 1_500L
        private const val SYNC_INTERVAL_MS = 150L
        private const val SEND_BUTTON_PARENT_DEPTH = 3

        /** 긴 글(말풍선 등)이 우연히 "보내기"를 포함해도 버튼으로 보지 않도록 */
        private const val MAX_SEND_LABEL_LENGTH = 20
    }
}
