import dev.extframework.gradle.common.archives
import dev.extframework.gradle.common.boot
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.extFramework
import dev.extframework.gradle.common.toolingApi

plugins {
    kotlin("jvm")
    id("dev.extframework.common")
}
group = "dev.extframework"
version = "1.0.1-BETA"

repositories {
    mavenCentral()
    extFramework()
    mavenLocal()
}

dependencies {
    testImplementation(kotlin("test"))
    toolingApi()
    artifactResolver()
    boot()
    archives()
    implementation(gradleApi())
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(8)
    explicitApi()
}

common {
    publishing {
        repositories {
            extFramework(credentials = propertyCredentialProvider)
        }

        publication {
            artifactId = "gradle-api"
            withJava()
            withSources()
            withDokka()

            commonPom {
                packaging = "jar"

                withExtFrameworkRepo()
                defaultDevelopers()
                gnuLicense()
            }
        }
    }
}