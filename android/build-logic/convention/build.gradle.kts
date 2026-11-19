plugins {
    `kotlin-dsl`
}

group = "com.emotionguard.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "emotionguard.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "emotionguard.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "emotionguard.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "emotionguard.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidFeature") {
            id = "emotionguard.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = "emotionguard.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("jvmLibrary") {
            id = "emotionguard.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
