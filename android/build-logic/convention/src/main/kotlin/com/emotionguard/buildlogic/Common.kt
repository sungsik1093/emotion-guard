package com.emotionguard.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

object AppConfig {
    const val COMPILE_SDK = 36
    const val TARGET_SDK = 36
    const val MIN_SDK = 26
    val JAVA = JavaVersion.VERSION_17
}

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalArgumentException("libs.versions.toml에 '$alias'가 없습니다") }

/** 앱·라이브러리 공통 안드로이드 설정 */
internal fun Project.configureAndroidCommon(extension: CommonExtension<*, *, *, *, *, *>) {
    extension.apply {
        compileSdk = AppConfig.COMPILE_SDK
        defaultConfig {
            minSdk = AppConfig.MIN_SDK
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = AppConfig.JAVA
            targetCompatibility = AppConfig.JAVA
        }
        testOptions {
            unitTests.isReturnDefaultValues = true
        }
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    dependencies {
        add("testImplementation", libs.lib("junit"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testImplementation", libs.lib("turbine"))
    }
}
