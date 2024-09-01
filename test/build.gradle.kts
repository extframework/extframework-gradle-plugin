import dev.extframework.gradle.common.boot
import dev.extframework.gradle.common.coreApi
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.common.extLoader
import dev.extframework.gradle.common.toolingApi
import dev.extframework.gradle.extframework
import dev.extframework.gradle.withExtension

plugins {
    kotlin("jvm") version "1.9.21"
    id("maven-publish")
    id("dev.extframework.mc") version "1.2"
    id("dev.extframework.common") version "1.0.16"
}

group = "dev.extframework.extensions"
version = "1.0-SNAPSHOT"

tasks.wrapper {
    gradleVersion = "8.6-rc-1"
}

tasks.launch {
    targetNamespace.set("mojang:deobfuscated")
    jvmArgs = listOf("-XstartOnFirstThread")
    mcVersion.set("1.21")
}

repositories {
    mavenCentral()
    mavenLocal()
    extframework()
}

extension {
    model {
        groupId.set("dev.extframework.extensions")
        name.set("extframework-ext-test")
        version.set("1.0-SNAPSHOT")
    }

    extensions {
        require("dev.extframework.extensions:extframework-ext-test-2:1.0-SNAPSHOT")
    }

    partitions {
        main {
            extensionClass = "dev.extframework.test.MyExtension"
            dependencies {
                coreApi()
            }
        }

        tweaker {
            tweakerClass = "dev.extframework.extensions.example.tweaker.TweakerEntry"
            dependencies {
                boot()
                toolingApi()
                jobs()
                artifactResolver(maven = true)
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("prod") {
            withExtension(project)
            artifactId = "extframework-ext-test"
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