import java.util.Properties

plugins {
    alias(libs.plugins.emotionguard.android.library)
    alias(libs.plugins.emotionguard.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// 서버 주소와 인증 설정은 local.properties에서 읽는다 (git에 올라가지 않음).
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun quoted(name: String, default: String = ""): String {
    val value = localProps.getProperty(name)?.trim().orEmpty().ifEmpty { default }
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "com.emotionguard.core.network"
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "PROXY_BASE_URL", quoted("PROXY_BASE_URL", "http://10.0.2.2:8787/"))
        buildConfigField("String", "DEV_KEY", quoted("DEV_KEY"))
        buildConfigField("String", "FIREBASE_APPLICATION_ID", quoted("FIREBASE_APPLICATION_ID"))
        buildConfigField("String", "FIREBASE_API_KEY", quoted("FIREBASE_API_KEY"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", quoted("FIREBASE_PROJECT_ID"))
        buildConfigField("String", "FIREBASE_PROJECT_NUMBER", quoted("FIREBASE_PROJECT_NUMBER"))
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.kotlinx.coroutines.play.services)
}
