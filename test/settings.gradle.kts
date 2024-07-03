rootProject.name = "test"

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