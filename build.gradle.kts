import dev.extframework.gradle.common.extFramework
import dev.extframework.gradle.common.*

plugins {
    kotlin("jvm") version "2.1.10"

    id("dev.extframework.common") version "1.1.2"
}

repositories {
    mavenCentral()
    extFramework()
}

tasks.wrapper {
    gradleVersion = "8.14.2"
}

allprojects {
    repositories {
        mavenLocal()
    }
}