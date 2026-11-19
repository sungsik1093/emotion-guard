plugins {
    alias(libs.plugins.emotionguard.android.application)
    alias(libs.plugins.emotionguard.android.compose)
    alias(libs.plugins.emotionguard.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.emotionguard.app"

    defaultConfig {
        applicationId = "com.emotionguard.app"
        versionCode = 1
        versionName = "2.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))

    implementation(project(":feature:guard"))
    implementation(project(":feature:home"))
    implementation(project(":feature:history"))
    implementation(project(":feature:character"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:onboarding"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
}
