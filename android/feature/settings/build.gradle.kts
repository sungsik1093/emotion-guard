plugins {
    alias(libs.plugins.emotionguard.android.feature)
}

android { namespace = "com.emotionguard.feature.settings" }

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))
}
