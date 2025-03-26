import dev.extframework.core.main.main
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.common.extFramework
import dev.extframework.gradle.common.toolingApi
import dev.extframework.minecraft.minecraft
import dev.extframework.minecraft.task.LaunchMinecraft

plugins {
    kotlin("jvm")
    id("maven-publish")
    id("dev.extframework")
    id("dev.extframework.common") version "1.0.52"
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

val launchLatest = tasks.registering(LaunchMinecraft::class) {
    mcVersion.set("1.21.4")
}

extension {
    model {
        attribute("reloadable", "false")
    }
    partitions {
        main {
            extensionClass = "dev.extframework.extension.test.Test2Entrypoint"
            dependencies {
                implementation("dev.extframework.core:entrypoint:1.0-BETA")
            }
        }
        minecraft("some_version") {
            mappings = mappers.getByName("mojang")
            dependencies {
                implementation("dev.extframework.core:entrypoint:1.0-BETA")
            }
        }
    }
}