import dev.extframework.gradle.MutableExtensionRepository
import dev.extframework.gradle.deobf.MinecraftMappings
import dev.extframework.gradle.extframework

plugins {
    kotlin("jvm") version "1.9.21"
    id("maven-publish")
    id("dev.extframework") version "1.1.4"
}

group = "dev.extframework"
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
    extframework()
}

dependencies {
}

tasks.jar {
    archiveBaseName.set("extframework-ext-test-2")
}

extension {
    model {
        groupId.set("dev.extframework.extensions")
        name.set("extframework-ext-test-2")
        version.set("1.0-SNAPSHOT")
        extensionRepositories {
            mavenLocal()
        }
        partitions {
            repositories {
                extframework()
                mavenLocal()
                mavenCentral()
//                add(
//                    MutableExtensionRepository(
//                        "fabric-mod:curse-maven",
//                        mutableMapOf(
//                            "location" to "http://localhost:3000"
//                        )
//                    )
//                )
            }
        }
    }

//    extensions {
//        fabricMod(
//            name = "jei",
//            projectId = "238222",
//            fileId = "5101365"
//        )
//        fabricMod(
//            name = "fabric-api",
//            projectId = "306612",
//            fileId = "5105683"
//        )
//    }

    partitions {
        version("latest") {
            supportVersions("1.20.1")
            mappings = MinecraftMappings.mojang

            dependencies {
                minecraft("1.20.1")
            }
        }

        version("notLatest") {
            supportVersions("1.19.1")
            mappings = MinecraftMappings.mojang

            dependencies {
                minecraft("1.19.1")
            }
        }

        main {
            extensionClass = "dev.extframework.extensions.test2.MyExtension2"
            dependencies {
                implementation("dev.extframework:client-api:1.2-SNAPSHOT")
            }
        }

        tweaker {
            tweakerClass = "dev.extframework.extensions.test2.TweakerTest2"
            dependencies {
                implementation("dev.extframework.components:ext-loader:1.1.1-SNAPSHOT")
                implementation("dev.extframework:boot:2.1.1-SNAPSHOT")

                implementation("dev.extframework:archives:1.2-SNAPSHOT")
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

            groupId = "dev.extframework.extensions"
            artifactId = "extframework-ext-test-2"
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