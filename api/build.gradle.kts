import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.extFramework
import dev.extframework.gradle.common.toolingApi

plugins {
    kotlin("jvm")
    id("dev.extframework.common")
}
group = "dev.extframework"
version = "1.0-BETA"

repositories {
    mavenCentral()
    extFramework()
    mavenLocal()
}

dependencies {
    testImplementation(kotlin("test"))
    toolingApi(version = "1.0.8-SNAPSHOT")
    artifactResolver()
    implementation("dev.extframework.core:app-api:1.0-BETA")
    implementation("dev.extframework.core:minecraft-api:1.0-BETA")
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