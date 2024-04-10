import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.reflect.Instantiator

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.9.21"

    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "net.yakclient"
version = "1.1"

tasks.wrapper {
    gradleVersion = "8.6-rc-1"
}

dependencies {
    implementation("com.durganmcbroom:artifact-resolver:1.1-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.1-SNAPSHOT") {
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
    implementation("net.yakclient:launchermeta-handler:1.1-SNAPSHOT")

    implementation("net.yakclient:archives:1.2-SNAPSHOT")

    implementation("net.yakclient:common-util:1.1-SNAPSHOT")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.14.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
    implementation("net.yakclient:object-container:1.0-SNAPSHOT") {
        isChanging = true
    }

    implementation("net.yakclient.components:ext-loader:1.1-SNAPSHOT") {
        isChanging = true
    }

    implementation("net.yakclient:boot:2.1-SNAPSHOT") {
        isChanging = true
    }
}

tasks.compileJava {
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
        mavenLocal()
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
