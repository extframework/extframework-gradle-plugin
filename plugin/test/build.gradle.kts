import dev.extframework.core.main.main
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.common.extFramework
import dev.extframework.gradle.common.toolingApi

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

extension {
    partitions {
        tweaker {
            tweakerClass = "dev.extframework.extensions.test2.TweakerTest2"
            dependencies {
                toolingApi()
                jobs()
            }
        }
        main {
            dependencies {

            }
        }
    }
}