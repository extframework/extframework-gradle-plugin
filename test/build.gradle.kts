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
    targetNamespace.set("mojang:deobfuscated")
    jvmArgs = listOf("-XstartOnFirstThread")
}

repositories {
    mavenCentral()
    mavenLocal()
    extframework()
}

dependencies {
    implementation("dev.extframework:client-api:1.2-SNAPSHOT")
}

extension {
    model {
        groupId.set("dev.extframework.extensions")
        name.set("extframework-ext-test")
        version.set("1.0-SNAPSHOT")
        extensionRepositories {
            mavenLocal()
        }
        partitions {
            repositories {
                extframework()
                mavenLocal()
                mavenCentral()
            }
        }
    }

    extensions {
        require("dev.extframework.extensions:extframework-ext-test-2:1.0-SNAPSHOT")
    }

    partitions {
        main {
            extensionClass = "dev.extframework.test.MyExtension"
        }

        tweaker {
            tweakerClass = "dev.extframework.extensions.example.tweaker.TweakerEntry"
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