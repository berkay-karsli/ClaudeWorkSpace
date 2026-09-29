pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Versions live here so each plugin is only resolved by the module that applies it.
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Reef"
include(":engine")
// -PengineOnly skips the Android app, for machines without access to Google's Maven repository.
if (!providers.gradleProperty("engineOnly").isPresent) include(":app")
// -PdesktopCheck type-checks the app's Compose UI against Compose Desktop (Maven Central only).
if (providers.gradleProperty("desktopCheck").isPresent) include(":desktopcheck")
