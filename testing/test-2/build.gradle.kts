//import dev.extframework.core.main.main
import dev.extframework.core.main.main
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.common.extFramework
import dev.extframework.gradle.common.toolingApi
import dev.extframework.minecraft.minecraft
//import dev.extframework.minecraft.task.LaunchMinecraft
import dev.extframework.minecraft.MojangNamespaces
import dev.extframework.minecraft.task.LaunchMinecraft

plugins {
    kotlin("jvm")
    id("maven-publish")
    id("dev.extframework")
    id("dev.extframework.common")
}

group = "dev.extframework.extension"
version = "1.0-BETA"

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = uri("https://repo.extframework.dev/registry")
    }
    extFramework()
}

val launch by tasks.registering(LaunchMinecraft::class) {
    mcVersion.set("1.21.4")
    targetNamespace.set(MojangNamespaces.deobfuscated.identifier)
    dependsOn(tasks.named("publishToMavenLocal"))
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
}

extension {
    model {
        attribute("reloadable", "false")
    }
    partitions {
        main {
            extensionClass = "dev.extframework.extension.test.Test2Entrypoint"
            dependencies {
            }
        }
        minecraft("some_version") {
            mappings = MojangNamespaces.deobfuscated
            entrypoint = "dev.extframework.extension.test2.Class"
            dependencies {
                minecraft("1.21.4")
            }
            supportVersions("1.21.4")
        }
        minecraft("second_version") {
            mappings = MojangNamespaces.deobfuscated
            entrypoint = "dev.extframework.extension.test2.Class"
            dependencies {
                minecraft("1.21")
            }
            supportVersions("1.21")
        }
        tweaker {
            tweakerClass = "dev.extframework.extensions.example.test2.Tweaker2Entry"
            dependencies {
                toolingApi()
                jobs()
            }
        }
    }
}