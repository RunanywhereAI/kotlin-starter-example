pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // Local Maven for development (SDK with new streamVoiceSession API)
        mavenLocal()
        google()
        mavenCentral()
        // JitPack for transitive dependencies (android-vad, PRDownloader)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "kotlin-starter-example"
include(":app")
