
rootProject.name = "extframework-gradle-plugin"

pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.extframework.dev/releases")
        }
        gradlePluginPortal()
    }
}