plugins {
    alias(libs.plugins.emotionguard.android.library)
    alias(libs.plugins.emotionguard.android.hilt)
}

android { namespace = "com.emotionguard.core.common" }

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}
