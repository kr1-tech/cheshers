pluginManagement {
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
        maven("https://jitpack.io") {
            content { includeGroup("com.github.bhlangonijr") }
        }
    }
}

rootProject.name = "Cheshers"
include(":app")
include(":core-chess")
include(":core-ai")
include(":core-data")
include(":feature-game")
include(":feature-quests")
include(":feature-profile")
include(":feature-online")
