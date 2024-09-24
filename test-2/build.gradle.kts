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
    id("dev.extframework.mc") version "1.2.4"
    id("dev.extframework.common") version "1.0.22"
}

group = "dev.extframework"
version = "1.0-BETA"

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

tasks.jar {
    archiveBaseName.set("extframework-ext-test-2")
}

tasks.launch {
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    mcVersion.set("1.21")
    targetNamespace.set(MinecraftMappings.mojang.deobfuscatedNamespace)
}

extension {
    model {
        groupId.set("dev.extframework.extensions")
        name.set("extframework-ext-test-2")
        version.set("1.0-BETA")
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
                minecraft("1.21")
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
            url = uri("http://localhost:8000")
            credentials {
                password = "a"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.compileKotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

tasks.compileJava {
    targetCompatibility = "21"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}