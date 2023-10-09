import net.yakclient.gradle.extensionInclude
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    kotlin("kapt") version "1.8.10"
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
        name = "yakgradle-ext-test"
        extensionClass = "net.yakclient.test.MyExtension"
    }

    mappingType = "mojang/deobfuscated"

    tweakerPartition {
        entrypoint.set("net.yakclient.extensions.example.tweaker.TweakerEntry")

        this.dependencies {
        }
    }

    partitions {
        val nineteen = create("nineteen_two") {
            this.dependencies {
                implementation(main)
                minecraft("1.19.2")
                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")

                implementation("net.yakclient:client-api:1.0-SNAPSHOT")
                "kaptNineteen_two"("net.yakclient:yakclient-preprocessor:1.0-SNAPSHOT")
            }

            supportedVersions.addAll(listOf("1.19.2", "1.18"))
        }

        create("eighteen") {
            this.dependencies {
                implementation(nineteen)
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