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
// Auto-provision the JDK named in gradle/gradle-daemon-jvm.properties (and any
// jvmToolchain) on machines/CI that don't already have it installed. Resolves
// download URLs via foojay's Disco API so a missing JDK 21 is fetched instead
// of failing the build.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "android_app"
include(":app")
include(":baselineprofile")
