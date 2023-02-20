rootProject.name = "test"

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven {
            isAllowInsecureProtocol = true
            url = uri("http://maven.yakclient.net/snapshots")
        }
        gradlePluginPortal()
    }
}