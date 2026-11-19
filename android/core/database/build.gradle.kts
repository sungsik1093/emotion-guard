plugins {
    alias(libs.plugins.emotionguard.android.library)
    alias(libs.plugins.emotionguard.android.hilt)
    alias(libs.plugins.emotionguard.android.room)
}

android { namespace = "com.emotionguard.core.database" }

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
}
