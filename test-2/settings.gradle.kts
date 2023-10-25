rootProject.name = "test-2"

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