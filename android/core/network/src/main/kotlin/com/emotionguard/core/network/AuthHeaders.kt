/**
 * 서버에 "누가, 어떤 앱으로" 요청하는지 알려주는 헤더를 만든다.
 *
 * - Firebase 설정이 local.properties에 있으면: 익명 로그인 ID 토큰 + App Check 토큰
 * - 없으면: dev 모드 (X-Dev-Key + X-Install-Id). 로컬 서버·시연용.
 *
 * 어느 쪽이든 앱 안에 Claude API 키는 없다. 키는 서버에만 있다.
 */
package com.emotionguard.core.network

import android.content.Context
import com.emotionguard.core.datastore.SettingsRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

interface AuthHeadersProvider {
    /** @param forceRefresh 401을 받은 뒤 토큰을 새로 받아야 할 때 true */
    suspend fun headers(forceRefresh: Boolean = false): Map<String, String>
}

object FirebaseBootstrap {
    val isConfigured: Boolean
        get() = BuildConfig.FIREBASE_APPLICATION_ID.isNotBlank() &&
            BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    /** Application.onCreate에서 한 번 호출. google-services.json 없이 설정값으로 초기화한다. */
    fun initialize(context: Context) {
        if (!isConfigured || FirebaseApp.getApps(context).isNotEmpty()) return
        val options = FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setGcmSenderId(BuildConfig.FIREBASE_PROJECT_NUMBER)
            .build()
        FirebaseApp.initializeApp(context, options)
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}

class FirebaseAuthHeaders @Inject constructor() : AuthHeadersProvider {
    override suspend fun headers(forceRefresh: Boolean): Map<String, String> {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
            ?: error("익명 로그인 실패")
        val idToken = user.getIdToken(forceRefresh).await().token ?: error("ID 토큰 없음")
        val appCheck = runCatching {
            FirebaseAppCheck.getInstance().getAppCheckToken(forceRefresh).await().token
        }.getOrNull()
        return buildMap {
            put("Authorization", "Bearer $idToken")
            if (!appCheck.isNullOrBlank()) put("X-Firebase-AppCheck", appCheck)
        }
    }
}

class DevAuthHeaders @Inject constructor(
    private val settings: SettingsRepository,
) : AuthHeadersProvider {
    override suspend fun headers(forceRefresh: Boolean): Map<String, String> = mapOf(
        "X-Dev-Key" to BuildConfig.DEV_KEY,
        "X-Install-Id" to settings.installId(),
    )
}
