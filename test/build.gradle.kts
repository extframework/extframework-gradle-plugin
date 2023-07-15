import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    kotlin("kapt") version "1.8.10"
    id("net.yakclient") version "1.0"
    id("maven-publish")

}

group = "net.yakclient"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        isAllowInsecureProtocol = true
        url = uri("http://maven.yakclient.net/snapshots")
    }
}

yakclient {
    model {
        groupId = "net.yakclient"
        name="test"
        version="1.0-SNAPSHOT"

        extensionClass = "net.yakclient.test.MyExtension"
    }

    partitions {
        val main by named {
            dependencies {

            }

            supportedVersions.addAll(listOf(""))
        }

        this.main = main

        val nineteen = named("nineteen_two") {
            dependencies {
                minecraft("1.19.2")
                implementation(main)
                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")

                implementation("net.yakclient:client-api:1.0-SNAPSHOT")
                "kaptNineteen_two"("net.yakclient:yakclient-preprocessor:1.0-SNAPSHOT")
            }

            supportedVersions.addAll(listOf("1.19.2", "1.18"))
        }

        named("eighteen") {
            dependencies {
                implementation(nineteen)
                implementation(main)
                minecraft("1.18")
                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")

                implementation("net.yakclient:client-api:1.0-SNAPSHOT")
                "kaptEighteen"("net.yakclient:yakclient-preprocessor:1.0-SNAPSHOT")

            }
            supportedVersions.addAll(listOf("1.18"))
        }
    }
}
tasks.compileJava {
    destinationDirectory.set(destinationDirectory.asFile.get().resolve("main"))
}

tasks.compileKotlin {
    destinationDirectory.set(tasks.compileJava.get().destinationDirectory.asFile.get())
}

tasks.compileTestJava {
    destinationDirectory.set(destinationDirectory.asFile.get().resolve("test"))
}

tasks.compileTestKotlin {
    destinationDirectory.set(tasks.compileTestJava.get().destinationDirectory.asFile.get())
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}