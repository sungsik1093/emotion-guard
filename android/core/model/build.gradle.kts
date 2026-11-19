plugins {
    alias(libs.plugins.emotionguard.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
