import dev.extframework.gradle.common.archives
import dev.extframework.gradle.common.boot
import dev.extframework.gradle.common.commonUtil
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.common.extFramework
import dev.extframework.gradle.common.objectContainer
import dev.extframework.gradle.common.toolingApi

plugins {
    kotlin("jvm")
    id("dev.extframework.common")
    `java-gradle-plugin`
}

group = "dev.extframework"
version = "1.0-BETA"

repositories {
    mavenCentral()
    extFramework()
    mavenLocal()
    maven {
        url = uri("https://repo.gradle.org/ui/native/libs-releases/")
    }
}

dependencies {
    implementation(project(":gradle-api"))
    toolingApi()
    boot()
    jobs()
    artifactResolver()
    archives()
    commonUtil()
    objectContainer()
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(8)
    explicitApi()
}