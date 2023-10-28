import groovy.util.Node
import groovy.util.NodeList
//import net.yakclient.gradle.extensionInclude
//import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    kotlin("kapt") version "1.8.10"
    id("maven-publish")
    id("net.yakclient") version "1.0.1"
}

group = "net.yakclient"
version = "1.0-SNAPSHOT"

tasks.launch {
    jvmArgs = listOf("-XstartOnFirstThread")
}

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        isAllowInsecureProtocol = true
        url = uri("http://maven.yakclient.net/snapshots")
    }
}

dependencies {
    implementation("net.yakclient:client-api:1.0-SNAPSHOT")
}

tasks.jar {
    archiveBaseName.set("yakgradle-ext-test")
}

yakclient {
    model {
        name = "yakgradle-ext-test-2"
        groupId = "net.yakclient.extensions"
        extensionClass = "net.yakclient.extensions.test2.MyExtension2"
    }

    mappingType = "mojang/deobfuscated"

    publications {
        groupId = "net.yakclient.extensions"
        artifactId = "yakgradle-ext-test-2-${name}"
    }

    tweakerPartition {
        entrypoint.set("net.yakclient.extensions.test2.TweakerTest2")

        this.dependencies {
            implementation("net.yakclient.components:ext-loader:1.0-SNAPSHOT")
            implementation("net.yakclient:boot:1.0-SNAPSHOT")
            implementation("net.yakclient:archives:1.1-SNAPSHOT")
            implementation("com.durganmcbroom:jobs:1.0-SNAPSHOT")
            implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT")
            implementation("com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT")
        }
    }

    partitions {
        val latest by creating {
            this.dependencies {
                implementation(main)
                minecraft("1.20.1")
                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")

                implementation("net.yakclient:client-api:1.0-SNAPSHOT")
                "kaptLatest"("net.yakclient:yakclient-preprocessor:1.0-SNAPSHOT")
            }

            supportedVersions.addAll(listOf("1.19.2", "1.20.1"))
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