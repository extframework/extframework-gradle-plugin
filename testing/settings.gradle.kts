rootProject.name = "testing"

pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.kaolinmc.com/releases")
        }
        maven {
            url = uri("https://maven.kaolinmc.com/snapshots")
        }
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}


include(":test-2")
include(":test")
include("library")
//include("test:library")
//findProject(":test:library")?.name = "library"
