import net.yakclient.gradle.yakclient

plugins {
    kotlin("jvm") version "1.9.21"
    id("maven-publish")
    id("net.yakclient") version "1.1.1"
}

group = "net.yakclient"
version = "1.0-SNAPSHOT"

tasks.wrapper {
    gradleVersion = "8.6-rc-1"
}

tasks.launch {
    targetNamespace.set("mojang:deobfuscated")
    jvmArgs = listOf("-XstartOnFirstThread")
}

repositories {
    mavenCentral()
    mavenLocal()
    yakclient()
}

dependencies {
    implementation("net.yakclient:client-api:1.0-SNAPSHOT")
}

tasks.jar {
    archiveBaseName.set("yakgradle-ext-test")
}

yakclient {
    model {
        groupId.set("net.yakclient.extensions")
        name.set("yakgradle-ext-test")
        version.set("1.0-SNAPSHOT")
        extensionRepositories {
            mavenLocal()
        }
        partitions {
            repositories {
                yakclient()
                mavenLocal()
                mavenCentral()
            }
        }
    }

    extensions {
        require("net.yakclient.extensions:yakgradle-ext-test-2:1.0-SNAPSHOT")
    }

    partitions {
        main {
            extensionClass = "net.yakclient.test.MyExtension"
        }

        tweaker {
            tweakerClass = "net.yakclient.extensions.example.tweaker.TweakerEntry"
            dependencies {
                implementation("net.yakclient.components:ext-loader:1.0-SNAPSHOT")
                implementation("net.yakclient:boot:2.1-SNAPSHOT")
                implementation("net.yakclient:archives:1.2-SNAPSHOT")
                implementation("com.durganmcbroom:jobs:1.2-SNAPSHOT")
                implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.1-SNAPSHOT")
                implementation("com.durganmcbroom:artifact-resolver:1.1-SNAPSHOT")
            }
        }

//        version("nineteen_two") {
//            supportVersions("1.19.2")
//            mappings = MinecraftMappings.mojang
//            dependencies {
//                implementation("net.yakclient:client-api:1.0-SNAPSHOT")
//                minecraft("1.19.2")
//            }
//        }
//
//        version("eighteen") {
//            supportVersions("1.18")
//            dependencies {
//                implementation("net.yakclient:client-api:1.0-SNAPSHOT")
//                minecraft("1.18")
//            }
//            mappings = MinecraftMappings.mojang
//        }
    }
}

publishing {
    publications {
        create<MavenPublication>("prod") {
            artifact(tasks.jar)
            artifact(tasks.generateErm) {
                classifier = "erm"
            }

            groupId = "net.yakclient.extensions"
            artifactId = "yakgradle-ext-test"
        }
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