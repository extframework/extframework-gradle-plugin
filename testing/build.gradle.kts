import com.kaolinmc.gradle.common.*

plugins {
    kotlin("jvm") version "2.1.10"

    id("kaolin.kiln") version "0.1.3" apply false
    id("com.kaolinmc.common") version "0.1.3"
}

repositories {
    mavenCentral()
    kaolin()
    mavenLocal()
}

dependencies {

}

tasks.wrapper {
    gradleVersion = "8.14.2"
}
