plugins {
    alias(libs.plugins.emotionguard.android.library)
    alias(libs.plugins.emotionguard.android.compose)
}

android { namespace = "com.emotionguard.core.designsystem" }

dependencies {
    api(project(":core:model"))
    implementation(libs.coil.compose)
}
