/**
 * 보호 중 상태 알림 (VPN 앱의 "연결됨" 알림처럼).
 * [끄기] 버튼을 누르면 GuardToggleReceiver가 보호를 끈다.
 */
package com.emotionguard.feature.guard.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.emotionguard.core.common.ApplicationScope
import com.emotionguard.core.datastore.SettingsRepository
import com.emotionguard.core.model.Accessory
import com.emotionguard.feature.guard.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class GuardNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun showActive() {
        if (!canNotify()) return
        ensureChannel()
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val turnOff = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, GuardToggleReceiver::class.java).setAction(GuardToggleReceiver.ACTION_TURN_OFF),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_guard)
            .setContentTitle("감정 가드 보호 중")
            .setContentText("메시지를 보내기 전에 감정 온도를 알려드려요.")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "끄기", turnOff)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "보호 상태", NotificationManager.IMPORTANCE_LOW).apply {
                description = "감정 가드가 켜져 있는 동안 표시됩니다."
                setShowBadge(false)
            },
        )
    }

    /** 레벨업 알림 (디자인 문서 §15.3) */
    fun showLevelUp(level: Int, unlocked: List<Accessory>) {
        val text = if (unlocked.isEmpty()) {
            "좋은 말 습관 덕분에 캐릭터가 자랐어요."
        } else {
            "새 장식이 열렸어요: ${unlocked.joinToString { it.label }}. 캐릭터 탭에서 달아보세요."
        }
        showInfo(GROWTH_CHANNEL_ID, "성장", LEVEL_UP_ID, "레벨 $level 달성!", text)
    }

    /** 주간 리포트 알림 (디자인 문서 §15.4). 메시지 원문은 넣지 않는다. */
    fun showWeeklyReport(title: String, body: String) {
        showInfo(REPORT_CHANNEL_ID, "주간 리포트", WEEKLY_REPORT_ID, title, body)
    }

    private fun showInfo(channelId: String, channelName: String, id: Int, title: String, text: String) {
        if (!canNotify()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_guard)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    companion object {
        const val CHANNEL_ID = "guard_status"
        const val NOTIFICATION_ID = 1001
        const val GROWTH_CHANNEL_ID = "growth"
        const val REPORT_CHANNEL_ID = "weekly_report"
        const val LEVEL_UP_ID = 1002
        const val WEEKLY_REPORT_ID = 1003
    }
}

@AndroidEntryPoint
class GuardToggleReceiver : BroadcastReceiver() {
    @Inject lateinit var settings: SettingsRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TURN_OFF) return
        val pending = goAsync()
        scope.launch {
            try {
                settings.setGuardEnabled(false)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TURN_OFF = "com.emotionguard.action.TURN_OFF"
    }
}
