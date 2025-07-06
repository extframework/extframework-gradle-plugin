import com.kaolinmc.gradle.common.*

plugins {
    kotlin("jvm")
    id("com.kaolinmc.common")
}

group = "com.kaolinmc"
version = "1.1.2-BETA"

repositories {
    mavenCentral()
    kaolin()
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
            kaolin(credentials = propertyCredentialProvider)
        }

        publication {
            artifactId = "gradle-api"
            withJava()
            withSources()
            withDokka()

            commonPom {
                packaging = "jar"

                withKaolinRepo()
                defaultDevelopers()
                gnuLicense()
            }
        }
    }
}