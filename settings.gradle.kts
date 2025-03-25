rootProject.name = "dev"

pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.extframework.dev/releases")
        }
        maven {
            url = uri("https://maven.extframework.dev/snapshots")
        }
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

include(":plugin")
findProject(":plugin")?.name = "extframework-gradle-plugin"
include("client")
findProject(":client")?.name = "dev-client"
include("api")
findProject(":api")?.name = "gradle-api"
include("tools")

include(":extframework-gradle-plugin:test-2")
include(":extframework-gradle-plugin:test")
include("extension")
