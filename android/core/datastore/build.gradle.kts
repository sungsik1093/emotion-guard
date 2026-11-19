plugins {
    alias(libs.plugins.emotionguard.android.library)
    alias(libs.plugins.emotionguard.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.emotionguard.core.datastore" }

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
}
