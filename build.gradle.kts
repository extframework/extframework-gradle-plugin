import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.9.21"

    id("com.gradle.plugin-publish") version "1.2.1"
    id("dev.extframework.common") version "1.0.23"
}

group = "dev.extframework.mc"
version = "1.2.14"

repositories {
    mavenLocal()
    mavenCentral()
    extFramework()
}

tasks.wrapper {
    gradleVersion = "8.6-rc-1"
}

dependencies {
    artifactResolver(maven = true)
    archiveMapper(transform = true, tiny = true, proguard = true)
    launcherMetaHandler()
    archives()
    commonUtil()
    boot()
    extLoader()
    toolingApi()
    objectContainer()
    jobs()

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.14.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    testImplementation(kotlin("test"))
}

gradlePlugin {
    website = "https://github.com/extframework"
    vcsUrl = "https://github.com/extframework/extframework-gradle-plugin"

    plugins {
        create("extframework") {
            id = "dev.extframework.mc"
            implementationClass = "dev.extframework.gradle.ExtFrameworkPlugin"
            displayName = "YakClient"
            description = "YakClient Gradle Plugin"
        }
    }
}

common {
    defaultJavaSettings()
    publishing {
        repositories {
            extFramework(credentials = propertyCredentialProvider, type = RepositoryType.RELEASES)
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
