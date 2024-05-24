plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.9.21"

    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "net.yakclient"
version = "1.1.2"

tasks.wrapper {
    gradleVersion = "8.6-rc-1"
}

val artifactResolverVersion = "1.1.2-SNAPSHOT"

dependencies {
    implementation("com.durganmcbroom:artifact-resolver:$artifactResolverVersion") {
        isChanging = true
    }
    implementation("com.durganmcbroom:artifact-resolver-simple-maven:$artifactResolverVersion") {
        isChanging = true
    }
    implementation("net.yakclient:archive-mapper-transform:1.2.1-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:archive-mapper:1.2.1-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:archive-mapper-proguard:1.2.1-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:archive-mapper-tiny:1.2.1-SNAPSHOT") {
        isChanging = true
    }

    implementation("net.yakclient:launchermeta-handler:1.1.2-SNAPSHOT")

    implementation("net.yakclient:archives:1.2-SNAPSHOT")

    implementation("net.yakclient:common-util:1.1.2-SNAPSHOT")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.14.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
    implementation("net.yakclient:object-container:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    implementation("net.yakclient.components:ext-loader:1.1-SNAPSHOT") {
        isChanging = true
    }

    implementation("net.yakclient:boot:2.1-SNAPSHOT") {
        isChanging = true
    }
}

gradlePlugin {
    website = "https://github.com/yakclient"
    vcsUrl = "https://github.com/yakclient/yakclient-gradle"

    plugins {
        create("yak") {
            id = "net.yakclient"
            implementationClass = "net.yakclient.gradle.YakClientGradle"
            displayName = "YakClient"
            description = "YakClient Gradle Plugin"
        }
    }
}

publishing {
    repositories {
        if (!project.hasProperty("maven-user") || !project.hasProperty("maven-pass")) return@repositories

        maven {
            val repo = if (project.findProperty("isSnapshot") == "true") "snapshots" else "releases"

            isAllowInsecureProtocol = true

            url = uri("http://maven.yakclient.net/$repo")

            credentials {
                username = project.findProperty("maven-user") as String
                password = project.findProperty("maven-pass") as String
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")

    configurations.all {
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }

    repositories {
        mavenCentral()
        maven {
            isAllowInsecureProtocol = true
            url = uri("http://maven.yakclient.net/snapshots")
        }
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        testImplementation(kotlin("test"))
    }

    tasks.compileJava {
        destinationDirectory.set(destinationDirectory.asFile.get().resolve("main"))
        targetCompatibility = "17"
        sourceCompatibility = "17"
    }

    tasks.compileKotlin {
        destinationDirectory.set(tasks.compileJava.get().destinationDirectory.asFile.get())

        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.compileTestJava {
        destinationDirectory.set(destinationDirectory.asFile.get().resolve("test"))
    }

    tasks.compileTestKotlin {
        destinationDirectory.set(tasks.compileTestJava.get().destinationDirectory.asFile.get())

        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.test {
        useJUnitPlatform()
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}
