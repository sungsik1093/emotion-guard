/**
 * 메신저 위 오버레이 창 관리.
 *
 * 창 두 개를 쓴다.
 * - 캐릭터 창: 작게 유지해서 메신저 터치를 가리지 않는다. 드래그로 옮기고, 놓으면 가장자리에 붙는다.
 * - 카드 창: 캐릭터를 누르면 펼쳐진다.
 *
 * TYPE_ACCESSIBILITY_OVERLAY라서 "다른 앱 위에 그리기" 권한이 필요 없다.
 * FLAG_NOT_FOCUSABLE이라서 떠 있는 동안에도 메신저에 계속 타이핑할 수 있다.
 */
package com.emotionguard.feature.guard.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.core.content.edit
import com.emotionguard.core.designsystem.theme.EmotionGuardTheme
import com.emotionguard.core.model.MoodState
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

class OverlayController(
    private val service: AccessibilityService,
    private val state: StateFlow<OverlayUiState>,
    private val resolveImage: (String) -> File,
    private val actions: OverlayActions,
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = service.getSharedPreferences("overlay_positions", Context.MODE_PRIVATE)
    private val density = service.resources.displayMetrics.density

    private var bubble: Window? = null
    private var card: Window? = null
    private var packageName: String? = null
    private var lastMood: MoodState = MoodState.IDLE

    private class Window(val view: ComposeView, val owner: OverlayLifecycleOwner, val params: WindowManager.LayoutParams)

    val isBubbleShown: Boolean get() = bubble != null

    fun showBubble(forPackage: String) {
        if (bubble != null && packageName == forPackage) return
        hideAll()
        packageName = forPackage
        val sizePx = ((BUBBLE_SIZE_DP + 12) * density).roundToInt()
        val params = baseParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("$forPackage.x", screenWidth() - sizePx)
            y = prefs.getInt("$forPackage.y", (screenHeight() * 0.25f).roundToInt())
        }
        bubble = createWindow(params) { window ->
            val ui by state.collectAsState()
            Box(
                Modifier
                    // TalkBack: 지금 상태와 점수를 읽고, 상태가 바뀌면 바로 알려준다. 두 번 탭하면 카드를 연다.
                    .clearAndSetSemantics {
                        contentDescription = bubbleDescription(ui)
                        liveRegion = LiveRegionMode.Polite
                        onClick(label = if (ui.cardVisible) "경고 카드 닫기" else "경고 카드 열기") {
                            actions.onBubbleTap()
                            true
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onTap = { actions.onBubbleTap() }) }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { snapToEdge(window, sizePx) },
                        ) { change, drag ->
                            change.consume()
                            window.params.x = (window.params.x + drag.x).roundToInt()
                                .coerceIn(0, screenWidth() - sizePx)
                            window.params.y = (window.params.y + drag.y).roundToInt()
                                .coerceIn(0, screenHeight() - sizePx)
                            windowManager.updateViewLayout(window.view, window.params)
                        }
                    },
            ) {
                FloatingBubble(ui, resolveImage)
                AttentionDot(ui, Modifier.align(Alignment.TopEnd))
            }
        }
    }

    fun setCardVisible(visible: Boolean) {
        if (!visible) {
            card?.let(::remove)
            card = null
            return
        }
        if (card != null || bubble == null) return
        val params = baseParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP
            y = (bubble!!.params.y + (BUBBLE_SIZE_DP + 16) * density).roundToInt()
                .coerceAtMost((screenHeight() * 0.55f).roundToInt())
        }
        card = createWindow(params) {
            val ui by state.collectAsState()
            WarningCard(ui, actions)
        }
    }

    /** 상태가 위험으로 바뀌는 순간 짧게 한 번 진동 */
    fun onMoodChanged(mood: MoodState) {
        if (mood == MoodState.DANGER && lastMood != MoodState.DANGER && bubble != null) vibrate()
        lastMood = mood
    }

    fun hideAll() {
        card?.let(::remove)
        card = null
        bubble?.let(::remove)
        bubble = null
        packageName = null
    }

    private fun bubbleDescription(ui: OverlayUiState): String = buildString {
        append("감정 가드 캐릭터, ").append(ui.mood.state.label)
        ui.mood.result?.takeIf { ui.mood.state != MoodState.THINKING }?.let { r ->
            append(", ").append(r.score).append("점")
            if (r.category != com.emotionguard.core.model.Category.NONE) append(", ").append(r.category.label)
            if (r.hasAlternative) append(", 대안 문장 ").append(r.alternatives.size).append("개")
        }
        ui.mood.error?.let { append(", ").append(it.message) }
    }

    private fun createWindow(
        params: WindowManager.LayoutParams,
        content: @androidx.compose.runtime.Composable (Window) -> Unit,
    ): Window {
        val owner = OverlayLifecycleOwner()
        val view = ComposeView(service)
        val window = Window(view, owner, params)
        owner.attachTo(view)
        view.setContent { EmotionGuardTheme { content(window) } }
        windowManager.addView(view, params)
        owner.resume()
        return window
    }

    private fun remove(window: Window) {
        runCatching { windowManager.removeView(window.view) }
        window.owner.destroy()
    }

    private fun snapToEdge(window: Window, sizePx: Int) {
        val center = window.params.x + sizePx / 2
        window.params.x = if (center < screenWidth() / 2) 0 else screenWidth() - sizePx
        runCatching { windowManager.updateViewLayout(window.view, window.params) }
        packageName?.let { pkg ->
            prefs.edit {
                putInt("$pkg.x", window.params.x)
                putInt("$pkg.y", window.params.y)
            }
        }
    }

    private fun baseParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    )

    private fun screenWidth() = service.resources.displayMetrics.widthPixels
    private fun screenHeight() = service.resources.displayMetrics.heightPixels

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (service.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            service.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        runCatching { vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
}
