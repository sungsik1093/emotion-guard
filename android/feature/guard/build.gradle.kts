plugins {
    alias(libs.plugins.emotionguard.android.feature)
}

android { namespace = "com.emotionguard.feature.guard" }

dependencies {
    implementation(project(":core:analysis"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.core.ktx)
}
