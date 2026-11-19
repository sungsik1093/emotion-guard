plugins {
    alias(libs.plugins.emotionguard.android.library)
    alias(libs.plugins.emotionguard.android.hilt)
}

android { namespace = "com.emotionguard.core.analysis" }

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
}
