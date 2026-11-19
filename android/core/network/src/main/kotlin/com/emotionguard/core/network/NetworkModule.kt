package com.emotionguard.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .apply {
            // 메시지 내용이 로그에 남지 않도록 디버그에서도 BASIC(주소·상태 코드)까지만 찍는다.
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    @Provides
    @Singleton
    fun proxyApi(client: OkHttpClient, json: Json): ProxyApi = Retrofit.Builder()
        .baseUrl(BuildConfig.PROXY_BASE_URL.let { if (it.endsWith("/")) it else "$it/" })
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ProxyApi::class.java)

    /** Firebase 설정이 있으면 Firebase 인증, 없으면 dev 인증 */
    @Provides
    @Singleton
    fun authHeaders(
        firebase: Provider<FirebaseAuthHeaders>,
        dev: Provider<DevAuthHeaders>,
    ): AuthHeadersProvider = if (FirebaseBootstrap.isConfigured) firebase.get() else dev.get()
}
