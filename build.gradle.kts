plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.7.10"

    id("com.gradle.plugin-publish") version "1.0.0"

}

group = "net.yakclient"
version = "1.0"

repositories {
    mavenCentral()
    maven {
        isAllowInsecureProtocol = true
        url = uri("http://maven.yakclient.net/snapshots")
    }
    maven {
        name = "Durgan McBroom GitHub Packages"
        url = uri("https://maven.pkg.github.com/durganmcbroom/artifact-resolver")
        credentials {
            username = project.findProperty("dm.gpr.user") as? String
                ?: throw IllegalArgumentException("Need a Github package registry username!")
            password = project.findProperty("dm.gpr.key") as? String
                ?: throw IllegalArgumentException("Need a Github package registry key!")
        }
    }
    mavenLocal()
}

dependencies {
    implementation("net.yakclient:archive-mapper-transform:1.1-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:archive-mapper:1.1-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:launchermeta-handler:1.0-SNAPSHOT")

    implementation("net.yakclient:archives:1.1-SNAPSHOT")

    implementation("net.yakclient:common-util:1.0-SNAPSHOT")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.14.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT")
    implementation("com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT")
}

pluginBundle {
    website = "https://github.com/yakclient"
    vcsUrl = "https://github.com/yakclient/yakclient-gradle"
    tags = listOf("")

}
gradlePlugin {
    plugins {
        create("yak") {
            id = "net.yakclient"
            implementationClass = "net.yakclient.gradle.YakClientGradle"
            displayName = "YakClient"
            description = "YakClient Gradle Plugin"
        }
    }

}

//task<Jar>("sourcesJar") {
//    archiveClassifier.set("sources")
//    from(sourceSets.main.get().allSource)
//}
//
//task<Jar>("javadocJar") {
//    archiveClassifier.set("javadoc")
//    from(tasks.dokkaJavadoc)
//}

//publishing {
//    publications {
//        create<MavenPublication>("yak-gradle-maven") {
////            from(components["java"])
////            artifact(tasks["sourcesJar"])
////            artifact(tasks["javadocJar"])
//
//            artifactId = "yakclient-gradle"
//
//            pom {
//                name.set("Yakclient Gradle")
//                description.set("YakClient Gradle plugin for creating extensions")
//                url.set("https://github.com/yakclient/yakclient-gradle")
//
//                packaging = "jar"
//
//                developers {
//                    developer {
//                        id.set("Chestly")
//                        name.set("Durgan McBroom")
//                    }
//                }
//                withXml {
//                    val repositoriesNode = asNode().appendNode("repositories")
//
//                    val yakRepoNode = repositoriesNode.appendNode("repository")
//                    yakRepoNode.appendNode("id", "yakclient")
//                    yakRepoNode.appendNode("url", "http://maven.yakclient.net/snapshots")
//                }
//
//                licenses {
//                    license {
//                        name.set("GNU General Public License")
//                        url.set("https://opensource.org/licenses/gpl-license")
//                    }
//                }
//
//                scm {
//                    connection.set("scm:git:git://github.com/yakclient/yakclient-gradle")
//                    developerConnection.set("scm:git:ssh://github.com:yakclient/yakclient-gradle.git")
//                    url.set("https://github.com/yakclient/yakclient-gradle")
//                }
//            }
//        }
//    }
//}



allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")

    configurations.all {
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }

    repositories {
        mavenCentral()
        maven {
            isAllowInsecureProtocol = true
            url = uri("http://maven.yakclient.net/snapshots")
        }
        maven {
            name = "Durgan McBroom GitHub Packages"
            url = uri("https://maven.pkg.github.com/durganmcbroom/artifact-resolver")
            credentials {
                username = project.findProperty("dm.gpr.user") as? String
                    ?: throw IllegalArgumentException("Need a Github package registry username!")
                password = project.findProperty("dm.gpr.key") as? String
                    ?: throw IllegalArgumentException("Need a Github package registry key!")
            }
        }
        mavenLocal()
    }

    publishing {
        repositories {
            if (!project.hasProperty("maven-user") || !project.hasProperty("maven-pass")) return@repositories

            maven {
                val repo = if (project.findProperty("isSnapshot") == "true") "snapshots" else "releases"

                isAllowInsecureProtocol = true

                url = uri("http://maven.yakclient.net/$repo")

                credentials {
                    username = project.findProperty("maven-user") as String
                    password = project.findProperty("maven-pass") as String
                }
                authentication {
                    create<BasicAuthentication>("basic")
                }
            }
        }
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        testImplementation(kotlin("test"))
    }

    tasks.compileJava {
        destinationDirectory.set(destinationDirectory.asFile.get().resolve("main"))
        targetCompatibility = "17"
        sourceCompatibility = "17"
    }

    tasks.compileKotlin {
        destinationDirectory.set(tasks.compileJava.get().destinationDirectory.asFile.get())

        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.compileTestJava {
        destinationDirectory.set(destinationDirectory.asFile.get().resolve("test"))
    }

    tasks.compileTestKotlin {
        destinationDirectory.set(tasks.compileTestJava.get().destinationDirectory.asFile.get())

        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.test {
        useJUnitPlatform()
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}
