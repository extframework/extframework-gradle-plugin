import com.kaolinmc.gradle.common.*
import com.kaolinmc.gradle.common.*

plugins {
    kotlin("jvm") version "2.1.10"

    id("com.kaolinmc.common") version "0.1.4"
}

repositories {
    mavenCentral()
    kaolin()
}

tasks.wrapper {
    gradleVersion = "8.14.2"
}

allprojects {
    repositories {}
}