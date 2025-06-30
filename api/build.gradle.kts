import dev.extframework.gradle.common.*

plugins {
    kotlin("jvm")
    id("dev.extframework.common")
}

group = "dev.extframework"
version = "1.1.1-BETA"

repositories {
    mavenCentral()
    extFramework()
}

dependencies {
    implementation(boot())
    implementation(extLoader())
    implementation(toolingApi())
    implementation(artifactResolver())
    implementation(artifactResolverMaven())
    implementation(objectContainer())
    implementation(archives())

    implementation(gradleApi())

    testImplementation(kotlin("test"))
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