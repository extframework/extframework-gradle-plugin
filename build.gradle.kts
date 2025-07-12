import com.kaolinmc.gradle.common.*
import com.kaolinmc.gradle.common.*

plugins {
    kotlin("jvm") version "2.1.10"

    id("com.kaolinmc.common") version "0.1.3"
}

dependencyManagement {
    boot("3.7.3-SNAPSHOT")
    extLoader("2.2.4-SNAPSHOT")
    toolingApi("1.1.3-SNAPSHOT")
}

repositories {
    mavenCentral()
    kaolin()
}

tasks.wrapper {
    gradleVersion = "8.14.2"
}

allprojects {
    repositories {
        mavenLocal()
    }
}