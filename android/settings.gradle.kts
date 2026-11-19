pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "EmotionGuard"

include(":app")

include(":core:model")
include(":core:common")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:analysis")
include(":core:designsystem")

include(":feature:guard")
include(":feature:home")
include(":feature:history")
include(":feature:character")
include(":feature:settings")
include(":feature:onboarding")
