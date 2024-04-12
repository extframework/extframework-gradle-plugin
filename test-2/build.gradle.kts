import net.yakclient.gradle.MutableExtensionRepository
import net.yakclient.gradle.deobf.MinecraftMappings
import net.yakclient.gradle.yakclient

plugins {
    kotlin("jvm") version "1.9.21"
    id("maven-publish")
    id("net.yakclient") version "1.1"
}

group = "net.yakclient"
version = "1.0-SNAPSHOT"

tasks.wrapper {
    gradleVersion = "8.6-rc-1"
}

tasks.launch {
    jvmArgs = listOf("-XstartOnFirstThread")
    targetNamespace.set("mojang:deobfuscated")
}

repositories {
    mavenCentral()
    mavenLocal()
    yakclient()
    maven {
        url = uri("http://localhost:3000")
        isAllowInsecureProtocol = true
    }
}

dependencies {
}

tasks.jar {
    archiveBaseName.set("yakgradle-ext-test-2")
}

yakclient {
    model {
        groupId.set("net.yakclient.extensions")
        name.set("yakgradle-ext-test-2")
        version.set("1.0-SNAPSHOT")
        extensionRepositories {
            mavenLocal()
        }
        partitions {
            repositories {
                yakclient()
                mavenLocal()
                mavenCentral()
                add(MutableExtensionRepository(
                    "fabric-mod:curse-maven",
                    mutableMapOf(
                        "location" to "http://localhost:3000"
                    )
                ))
            }
        }
    }

    extensions {
        fabricMod(
            name = "jei",
            projectId = "238222",
            fileId = "5101365"
        )
        fabricMod(
            name = "fabric-api",
            projectId = "306612",
            fileId = "5105683"
        )
    }

    partitions {
        version("latest") {
            supportVersions("1.20.1")
            mappings = MinecraftMappings.mojang

            dependencies {
                minecraft("1.20.1")
            }
        }

        main {
            extensionClass = "net.yakclient.extensions.test2.MyExtension2"
            dependencies {
                implementation("net.yakclient:client-api:1.0-SNAPSHOT")
            }
        }

        tweaker {
            tweakerClass = "net.yakclient.extensions.test2.TweakerTest2"
            dependencies {
                implementation("net.yakclient.components:ext-loader:1.0-SNAPSHOT")
                implementation("net.yakclient:boot:2.1-SNAPSHOT")

                implementation("net.yakclient:archives:1.2-SNAPSHOT")
                implementation("com.durganmcbroom:jobs:1.2-SNAPSHOT")
                implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.1-SNAPSHOT")
                implementation("com.durganmcbroom:artifact-resolver:1.1-SNAPSHOT")
            }
        }
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
            artifactId = "yakgradle-ext-test-2"
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