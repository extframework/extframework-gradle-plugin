plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.7.10"

    id("com.gradle.plugin-publish") version "1.0.0"
}

group = "net.yakclient"
version = "1.0.2"

dependencies {
    implementation("io.arrow-kt:arrow-core:1.1.2")
    implementation("com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:archive-mapper-transform:1.1-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:archive-mapper:1.1-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:launchermeta-handler:1.0-SNAPSHOT")

    implementation("net.yakclient:archives:1.1-SNAPSHOT")

    implementation("net.yakclient:common-util:1.0-SNAPSHOT")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.14.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
    implementation("net.yakclient:object-container:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    implementation("net.yakclient.components:ext-loader:1.0-SNAPSHOT") {
        isChanging = true
    }

    implementation("net.yakclient:boot:1.0-SNAPSHOT") {
        isChanging = true
    }
}

pluginBundle {
    website = "https://github.com/yakclient"
    vcsUrl = "https://github.com/yakclient/yakclient-gradle"
    tags = listOf("")
}
gradlePlugin {
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
        mavenLocal()
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
