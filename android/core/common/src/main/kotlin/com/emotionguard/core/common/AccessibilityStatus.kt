/**
 * 감정 가드 접근성 서비스가 시스템 설정에서 켜져 있는지 확인한다.
 *
 * 안드로이드는 앱이 접근성 서비스를 직접 켜지 못하게 막는다. 그래서 온보딩·홈 화면은
 * 이 값을 보고 "설정에서 켜주세요" 안내를 띄운다.
 */
package com.emotionguard.core.common

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val component: ComponentName
        get() = ComponentName(context.packageName, SERVICE_CLASS)

    fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { entry ->
            ComponentName.unflattenFromString(entry) == component
        }
    }

    /** 시스템 접근성 설정 화면을 여는 인텐트 */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        /** feature:guard 모듈의 서비스. 모듈 간 의존을 피하려고 이름으로만 참조한다. */
        const val SERVICE_CLASS = "com.emotionguard.feature.guard.EmotionGuardService"
    }
}
