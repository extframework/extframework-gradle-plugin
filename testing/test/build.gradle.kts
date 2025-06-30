import dev.extframework.gradle.common.extFramework
import dev.extframework.gradle.common.toolingApi

plugins {
    kotlin("jvm")
    id("maven-publish")
    id("dev.extframework")
    id("dev.extframework.common")
}

group = "dev.extframework.extension"
version = "1.0-BETA"

repositories {
    mavenLocal()
    mavenCentral()
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
               implementation(toolingApi())
            }
        }
        gradle {

        }
    }
}

dependencies {
}