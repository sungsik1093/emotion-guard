import androidx.room.gradle.RoomExtension
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.emotionguard.buildlogic.AppConfig
import com.emotionguard.buildlogic.configureAndroidCommon
import com.emotionguard.buildlogic.lib
import com.emotionguard.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** :app 모듈 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")
        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            defaultConfig.targetSdk = AppConfig.TARGET_SDK
        }
    }
}

/** 안드로이드 라이브러리 모듈 (core:*) */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")
        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)
        }
    }
}

/** Jetpack Compose 사용 (앱·라이브러리 공통) */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        val android = extensions.getByType(CommonExtension::class.java)
        android.buildFeatures.compose = true
        dependencies {
            val bom = platform(libs.lib("androidx-compose-bom"))
            add("implementation", bom)
            add("implementation", libs.lib("androidx-compose-ui"))
            add("implementation", libs.lib("androidx-compose-ui-graphics"))
            add("implementation", libs.lib("androidx-compose-foundation"))
            add("implementation", libs.lib("androidx-compose-material3"))
            add("implementation", libs.lib("androidx-compose-material-icons-core"))
            add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
            add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
        }
    }
}

/** Hilt 의존성 주입 (KSP) */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        dependencies {
            add("implementation", libs.lib("hilt-android"))
            add("ksp", libs.lib("hilt-compiler"))
        }
    }
}

/** 화면 기능 모듈 (feature:*) = 라이브러리 + Compose + Hilt + 내비게이션 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("emotionguard.android.library")
        pluginManager.apply("emotionguard.android.compose")
        pluginManager.apply("emotionguard.android.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("androidx-hilt-navigation-compose"))
            add("implementation", libs.lib("kotlinx-serialization-json"))
        }
    }
}

/** Room DB: 스키마 JSON을 schemas/에 남겨 마이그레이션을 검증할 수 있게 한다 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("androidx.room")
        pluginManager.apply("com.google.devtools.ksp")
        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }
        dependencies {
            add("implementation", libs.lib("androidx-room-runtime"))
            add("implementation", libs.lib("androidx-room-ktx"))
            add("ksp", libs.lib("androidx-room-compiler"))
        }
    }
}

/** 안드로이드에 의존하지 않는 순수 Kotlin 모듈 (core:model) */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = AppConfig.JAVA
            targetCompatibility = AppConfig.JAVA
        }
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
        dependencies {
            add("testImplementation", libs.lib("junit"))
        }
    }
}
