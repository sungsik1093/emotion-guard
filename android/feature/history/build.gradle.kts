plugins {
    alias(libs.plugins.emotionguard.android.feature)
}

android { namespace = "com.emotionguard.feature.history" }

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
}
