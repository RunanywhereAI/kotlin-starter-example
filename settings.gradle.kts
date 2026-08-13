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
// Plane B / local SDK bring-up: consume `publishToMavenLocal` AARs from a
// runanywhere-sdks checkout ahead of Maven Central. Developer-only, opt-in per
// invocation (`-Prunanywhere.useLocalSdkAars=true`); never commit it to
// gradle.properties, and never enable it in CI — the whole point of this repo's
// CI gate is to prove a clean clone resolves the SDK from Maven Central.
val useLocalSdkAars = settings.providers.gradleProperty("runanywhere.useLocalSdkAars")
    .map { it.toBoolean() }
    .orElse(false)
    .get()

// A locally published AAR carries the SAME coordinates as the released one
// (io.github.sanchitmonga22:<artifact>:<version>, straight from the monorepo's
// `version` + `group`) but different bytes, so its sha256 will NOT match
// gradle/verification-metadata.xml. Dependency verification is auto-enabled by
// the mere presence of that file, so the build fails with "artifacts failed
// verification" the moment this flag is on. That is the gate doing its job, not
// a bug — relax it per invocation instead of weakening the committed metadata.
if (useLocalSdkAars) {
    logger.lifecycle(
        "runanywhere.useLocalSdkAars=true: resolving io.github.sanchitmonga22 from mavenLocal(). " +
            "Locally built AARs cannot match the sha256 entries in gradle/verification-metadata.xml, " +
            "so this run also needs --dependency-verification=lenient. " +
            "Do NOT add trusted-artifacts entries for that group to make this go away.",
    )
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useLocalSdkAars) {
            // Scoped to the SDK group on purpose. An unscoped mavenLocal() sits ahead of
            // google() and mavenCentral() for EVERY module, so one stale ~/.m2 artifact
            // for an unrelated dependency would silently shadow the verified copy.
            mavenLocal {
                content { includeGroup("io.github.sanchitmonga22") }
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "RunAnywhere"
include(":app")
