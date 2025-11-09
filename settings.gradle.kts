pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    // Optional: resolutionStrategy if you need to force plugin versions
    plugins {
        // declare the kotlin gradle plugin here so it's resolved consistently
        id("org.jetbrains.kotlin.jvm") version "1.9.20"
        id("com.android.application") version "8.1.2"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "IrrigCtrl-Android"
include(":app")