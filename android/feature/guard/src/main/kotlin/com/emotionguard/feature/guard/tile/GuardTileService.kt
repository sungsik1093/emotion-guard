/**
 * 빠른 설정 타일: 상단바를 내려서 바로 보호를 켜고 끈다 (VPN 타일처럼).
 * 접근성 서비스가 아직 꺼져 있으면 앱을 열어 안내한다.
 */
package com.emotionguard.feature.guard.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.emotionguard.core.common.AccessibilityStatus
import com.emotionguard.core.datastore.SettingsRepository
import com.emotionguard.feature.guard.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GuardTileService : TileService() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var accessibility: AccessibilityStatus

    private val scope = MainScope()
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listenJob?.cancel()
        listenJob = scope.launch {
            settings.settings.collect { render(it.guardEnabled) }
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (!accessibility.isServiceEnabled()) {
            openApp()
            return
        }
        scope.launch { render(settings.toggleGuard()) }
    }

    private fun render(enabled: Boolean) {
        val tile = qsTile ?: return
        val available = accessibility.isServiceEnabled()
        tile.state = when {
            !available -> Tile.STATE_UNAVAILABLE
            enabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = "감정 가드"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !available -> "설정 필요"
                enabled -> "보호 중"
                else -> "꺼짐"
            }
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_guard)
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
