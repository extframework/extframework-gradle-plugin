import dev.extframework.gradle.common.extFramework

plugins {
    kotlin("jvm") version "2.1.10"

    id("dev.extframework") version "1.4.1" apply false
    id("dev.extframework.common") version "1.1"
}

repositories {
    mavenCentral()
    extFramework()
    mavenLocal()
}

dependencies {

}

tasks.wrapper {
    gradleVersion = "8.14.2"
}
