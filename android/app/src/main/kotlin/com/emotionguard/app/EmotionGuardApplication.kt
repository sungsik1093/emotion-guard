package com.emotionguard.app

import android.app.Application
import com.emotionguard.app.report.WeeklyReportWorker
import com.emotionguard.core.common.ApplicationScope
import com.emotionguard.core.datastore.SettingsRepository
import com.emotionguard.core.network.FirebaseBootstrap
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class EmotionGuardApplication : Application() {

    @Inject lateinit var settings: SettingsRepository

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // local.properties에 Firebase 설정이 있을 때만 초기화된다. 없으면 dev 인증으로 동작.
        FirebaseBootstrap.initialize(this)

        // 주간 리포트: 설정이 켜져 있으면 예약, 꺼지면 취소
        appScope.launch {
            settings.settings.map { it.weeklyReportEnabled }.distinctUntilChanged().collect { enabled ->
                if (enabled) WeeklyReportWorker.schedule(this@EmotionGuardApplication)
                else WeeklyReportWorker.cancel(this@EmotionGuardApplication)
            }
        }
    }
}
