import dev.extframework.gradle.common.coreApi
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.common.toolingApi
import dev.extframework.gradle.deobf.MinecraftMappings
import dev.extframework.gradle.extframework
import dev.extframework.gradle.publish.ExtensionPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "1.9.21"
    id("maven-publish")
    id("dev.extframework.mc") version "1.2.24"
    id("dev.extframework.common") version "1.0.38"
}

group = "dev.extframework.extension"
version = "1.0-BETA"

tasks.wrapper {
    gradleVersion = "8.6-rc-1"
}

tasks.launch {
//    jvmArgs("-XstartOnFirstThread")
    targetNamespace.set("mojang:obfuscated")
}

repositories {
    mavenCentral()
    mavenLocal()
    extframework()
    maven {
        url = uri("https://repo.extframework.dev/registry")
    }
}

tasks.jar {
    archiveBaseName.set("extframework-ext-test-2")
}

extension {
    model {
        name.set("extframework-ext-test-2")
        repositories {
            mavenLocal()
        }
    }
    metadata {
        name.set("Magic Mo Shield")
        description.set("A mod that adds a Mo shield!")
        icon.set("https://cdn.pixabay.com/photo/2023/01/18/10/32/ouch-7726461_640.jpg")
    }

    partitions {
        version("latest") {
            supportVersions("1.21")
            mappings = MinecraftMappings.mojang

            dependencies {
                fabricMod(
                    "P7dR8mSH",
                    "Oh9IKZRD"
                    )
                minecraft("1.21")
            }
        }

        version("legacy") {
            supportVersions("1.8.9")
            mappings = MinecraftMappings.mcpLegacy

            dependencies {
                minecraft("1.8.9")
            }
        }

        main {
            extensionClass = "dev.extframework.extensions.test2.MyExtension2"
            dependencies {
                coreApi()
            }
        }

        tweaker {
            model {
                repositories {
                    extframework()
                    mavenLocal()
                }
            }
            tweakerClass = "dev.extframework.extensions.test2.TweakerTest2"
            dependencies {
                toolingApi()
                jobs()
            }
        }
    }
}

publishing {
    publications {
        create<ExtensionPublication>("prod")
    }
    repositories {
        maven {
            url = uri("http://127.0.0.1:6969")
            credentials {
                password = "a"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

//java {
//    toolchain {
//        vendor = JvmVendorSpec.matching("Eclipse Temurin")
//        languageVersion.set(JavaLanguageVersion.of(8))
//    }
//}