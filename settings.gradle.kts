rootProject.name = "kiln"

pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.kaolinmc.com/releases")
        }
        maven {
            url = uri("https://maven.kaolinmc.com/snapshots")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

include(":plugin")
findProject(":plugin")
include("api")
findProject(":api")