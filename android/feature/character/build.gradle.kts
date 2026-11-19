plugins {
    alias(libs.plugins.emotionguard.android.feature)
}

android { namespace = "com.emotionguard.feature.character" }

dependencies {
    implementation(project(":core:datastore"))
    implementation(libs.androidx.activity.compose)
}
