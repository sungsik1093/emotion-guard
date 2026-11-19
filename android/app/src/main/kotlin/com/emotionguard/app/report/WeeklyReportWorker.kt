/**
 * 주간 리포트 알림 (디자인 문서 §15.4).
 *
 * - 매주 일요일 20시 전후에 WorkManager가 실행한다. 설정에서 끄면 예약을 취소한다.
 * - 설정의 [지금 리포트 받아보기]는 같은 작업을 한 번만 바로 실행한다.
 * - 의존성은 Hilt EntryPoint로 받는다 (WorkerFactory 설정 없이 기본 WorkManager 초기화를 그대로 쓰기 위해).
 */
package com.emotionguard.app.report

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.emotionguard.core.database.HistoryRepository
import com.emotionguard.core.database.WeeklyReport
import com.emotionguard.core.datastore.GrowthRepository
import com.emotionguard.core.datastore.SettingsRepository
import com.emotionguard.feature.guard.notification.GuardNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

class WeeklyReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun history(): HistoryRepository
        fun growth(): GrowthRepository
        fun settings(): SettingsRepository
        fun notifier(): GuardNotifier
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val forced = inputData.getBoolean(KEY_FORCE, false)
        if (!forced && !deps.settings().current().weeklyReportEnabled) return Result.success()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.minusDays(6).atStartOfDay(zone)
        val end = today.plusDays(1).atStartOfDay(zone)
        val thisWeek = deps.history().between(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())
        val lastWeek = deps.history().between(
            start.minusDays(7).toInstant().toEpochMilli(),
            start.toInstant().toEpochMilli(),
        )
        val report = WeeklyReport.build(thisWeek, lastWeek, deps.growth().current())
        deps.notifier().showWeeklyReport(report.title, report.body)
        return Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "weekly_report"
        private const val NOW_NAME = "weekly_report_now"
        private const val KEY_FORCE = "force"
        private val SEND_TIME: LocalTime = LocalTime.of(20, 0)

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNextSunday(ZonedDateTime.now()).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            // KEEP: 앱을 켤 때마다 다시 예약해도 이미 있는 일정은 그대로 둔다
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        }

        /** 시연용: 지금 바로 한 번 */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<WeeklyReportWorker>()
                .setInputData(workDataOf(KEY_FORCE to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /** 다음 일요일 20시까지 남은 시간. 지금이 일요일 20시 전이면 오늘 20시 */
        fun delayUntilNextSunday(now: ZonedDateTime): Duration {
            var target = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).with(SEND_TIME)
            if (!target.isAfter(now)) target = target.plusWeeks(1)
            return Duration.between(now, target)
        }
    }
}
