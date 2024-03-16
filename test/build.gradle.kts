import net.yakclient.gradle.MojangMappingProvider
import net.yakclient.gradle.MutableExtensionRepository

plugins {
    kotlin("jvm") version "1.9.21"
    id("maven-publish")
    id("net.yakclient") version "1.0.3"
}

group = "net.yakclient"
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
    maven {
        isAllowInsecureProtocol = true
        url = uri("http://maven.yakclient.net/snapshots")
    }
}

dependencies {
    implementation("net.yakclient:client-api:1.0-SNAPSHOT")
    implementation(yakclient.tweakerPartition.map { it.sourceSet.output })
}

tasks.jar {
    archiveBaseName.set("yakgradle-ext-test")
}

yakclient {
    model {
        name.set("yakgradle-ext-test")
        groupId.set("net.yakclient.extensions")
        version.set("1.0-SNAPSHOT")
        extensionClass.set("net.yakclient.test.MyExtension")

        mainPartition.update {
            it.map { partition ->
                partition.repositories.add(
                    MutableExtensionRepository(
                        "fabric",
                        mutableMapOf()
                    )
                )
                partition
            }
        }
    }

    tweakerPartition {
        entrypoint.set("net.yakclient.extensions.example.tweaker.TweakerEntry")

        this.dependencies {
            implementation("net.yakclient.components:ext-loader:1.0-SNAPSHOT")
            implementation("net.yakclient:boot:2.1-SNAPSHOT")
            implementation("net.yakclient:archives:1.2-SNAPSHOT")
            implementation("com.durganmcbroom:jobs:1.2-SNAPSHOT")
            implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.1-SNAPSHOT")
            implementation("com.durganmcbroom:artifact-resolver:1.1-SNAPSHOT")
        }
    }

    partitions {
        val nineteen_two by creating {
            this.dependencies {
                implementation(main)
                minecraft("1.19.2")
                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")

                implementation("net.yakclient:client-api:1.0-SNAPSHOT")
//                "kaptNineteen_two"("net.yakclient:yakclient-preprocessor:1.0-SNAPSHOT")
            }

            mappingsType.set("mojang")

            supportedVersions.addAll(listOf("1.19.2", "1.18"))
        }

//        create("eighteen") {
//            this.dependencies {
//                implementation(nineteen)
//                minecraft("1.18")
//                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")
//
//                implementation("net.yakclient:client-api:1.0-SNAPSHOT")
//                "kaptEighteen"("net.yakclient:yakclient-preprocessor:1.0-SNAPSHOT")
//            }
//
//            supportedVersions.addAll(listOf("1.18"))
//        }
    }

    extension("net.yakclient.extensions:yakgradle-ext-test-2:1.0-SNAPSHOT")
}

publishing {
    publications {
        create<MavenPublication>("prod") {
            artifact(tasks.jar)
            artifact(tasks.generateErm) {
                classifier = "erm"
            }

            groupId = "net.yakclient.extensions"
            artifactId = "yakgradle-ext-test"
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