rootProject.name = "test-2"

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven {
            url = uri("https://maven.extframework.dev/snapshots")
        }

        gradlePluginPortal()
    }
}