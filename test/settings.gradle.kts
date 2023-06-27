rootProject.name = "test"

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven {
            isAllowInsecureProtocol = true
            url = uri("http://maven.yakclient.net/snapshots")
        }
        maven {
            name = "Durgan McBroom GitHub Packages"
            url = uri("https://maven.pkg.github.com/durganmcbroom/artifact-resolver")
            credentials {
                username = settings.extra.properties["dm.gpr.user"] as? String
                        ?: throw IllegalArgumentException("Need a Github package registry username!")
                password = settings.extra.properties["dm.gpr.key"] as? String
                        ?: throw IllegalArgumentException("Need a Github package registry key!")
            }
        }
        gradlePluginPortal()
    }
}