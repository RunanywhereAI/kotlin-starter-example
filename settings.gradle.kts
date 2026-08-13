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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val useLocalSdkAars = settings.providers.gradleProperty("runanywhere.useLocalSdkAars")
        .map { it.toBoolean() }
        .orElse(false)
        .get()
    repositories {
        // Plane B / local SDK bring-up: consume publishToMavenLocal AARs ahead of Maven Central.
        if (useLocalSdkAars) mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "RunAnywhere"
include(":app")
