
rootProject.name = "yakclient-gradle"

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.dokka") version "1.9.10"
    }
}