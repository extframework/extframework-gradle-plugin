plugins {
    id("maven-publish")
    id("org.jetbrains.dokka") version "1.6.0"
}

group = "net.yakclient"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.yakclient:common-util:1.0-SNAPSHOT")
    implementation("net.yakclient:client-api:1.0-SNAPSHOT")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.14.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
}


task<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

task<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc)
}

publishing {
    publications {
        create<MavenPublication>("yakclient-preprocessor-maven") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            artifactId = "yakclient-preprocessor"

            pom {
                name.set("Yakclient Preprocessor")
                description.set("The YakClient Preprocessor for making ERM generation seamless")
                url.set("https://github.com/yakclient/yakclient-gradle")

                packaging = "jar"

                developers {
                    developer {
                        id.set("Chestly")
                        name.set("Durgan McBroom")
                    }
                }
                withXml {
                    val repositoriesNode = asNode().appendNode("repositories")

                    val yakRepoNode = repositoriesNode.appendNode("repository")
                    yakRepoNode.appendNode("id", "yakclient")
                    yakRepoNode.appendNode("url", "http://maven.yakclient.net/snapshots")
                }

                licenses {
                    license {
                        name.set("GNU General Public License")
                        url.set("https://opensource.org/licenses/gpl-license")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/yakclient/yakclient-gradle")
                    developerConnection.set("scm:git:ssh://github.com:yakclient/yakclient-gradle.git")
                    url.set("https://github.com/yakclient/yakclient-gradle")
                }
            }
        }
    }
}