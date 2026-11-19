/**
 * 감정 가드 접근성 서비스. 앱 전체 흐름의 시작점이다.
 *
 * 이 클래스는 얇게 유지한다: 이벤트를 감지 계층에 넘기고, 오버레이와 세션을 연결할 뿐이다.
 * 실제 흐름은 GuardSession, 감지는 AccessibilityInputSource, 화면은 OverlayController에 있다.
 */
package com.emotionguard.feature.guard

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.emotionguard.core.common.DiagnosticsBus
import com.emotionguard.core.datastore.SettingsRepository
import com.emotionguard.core.model.MoodState
import com.emotionguard.feature.guard.input.AccessibilityInputSource
import com.emotionguard.feature.guard.notification.GuardNotifier
import com.emotionguard.feature.guard.overlay.OverlayController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EmotionGuardService : AccessibilityService() {

    @Inject lateinit var session: GuardSession
    @Inject lateinit var diagnostics: DiagnosticsBus
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var notifier: GuardNotifier

    private val scope = MainScope()
    private var source: AccessibilityInputSource? = null
    private var overlay: OverlayController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val input = AccessibilityInputSource(this, scope, diagnostics)
        val controller = OverlayController(
            service = this,
            state = session.ui,
            resolveImage = session::resolveImage,
            actions = session,
        )
        source = input
        overlay = controller

        session.start(
            scope = scope,
            source = input,
            overlay = object : GuardSession.Overlay {
                override fun show(packageName: String) = controller.showBubble(packageName)
                override fun hide() = controller.hideAll()
                override fun setCardVisible(visible: Boolean) = controller.setCardVisible(visible)
                override fun onMoodChanged(mood: MoodState) = controller.onMoodChanged(mood)
            },
        )

        // 보호가 켜져 있는 동안 상태 알림을 띄운다
        scope.launch {
            settings.settings.map { it.guardEnabled }.distinctUntilChanged().collect { enabled ->
                if (enabled) notifier.showActive() else notifier.cancel()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        source?.onEvent(event)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        overlay?.hideAll()
        notifier.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
