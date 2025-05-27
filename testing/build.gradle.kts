import dev.extframework.gradle.common.extFramework

plugins {
    kotlin("jvm") version "2.1.10"

    id("dev.extframework") version "1.3.1" apply false
    id("dev.extframework.common") version "1.0.52"
}

repositories {
    mavenCentral()
    extFramework()
    mavenLocal()
}

dependencies {

}

tasks.wrapper {
    gradleVersion = "8.6-rc-1"
}
