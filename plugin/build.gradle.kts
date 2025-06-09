import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.common.dm.resourceApi
import org.gradle.kotlin.dsl.DependencyHandlerScope

plugins {
    `java-gradle-plugin`
    kotlin("jvm")

    id("com.gradle.plugin-publish")
    id("dev.extframework.common")
    id("com.gradleup.shadow") version "9.0.0-beta10"
}

group = "dev.extframework"
version = "1.3.4"

repositories {
    mavenCentral()
    extFramework()
    mavenLocal()
}

val jacksonConfig by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

fun DependencyHandlerScope.jackson(
    notation: Any
) {
    jacksonConfig(notation)
    compileOnly(notation)
}

dependencies {
    constraints {
        implementation("com.fasterxml.jackson.core:jackson-core") {
            version {
                strictly("2.18.3")
            }
        }
    }

    boot(configurationName = "shadow")
    extLoader(configurationName = "shadow")
    toolingApi(configurationName = "shadow")

    artifactResolver(configurationName = "shadow", maven = true)
    archiveMapper(configurationName = "shadow", transform = true)
    archives(configurationName = "shadow")
    commonUtil(configurationName = "shadow")
    toolingApi(configurationName = "shadow")
    objectContainer(configurationName = "shadow")
    jobs(configurationName = "shadow")
    resourceApi()

    shadow(project(":api"))
    jackson("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.18.3")
    jackson("com.fasterxml.jackson.core:jackson-core:2.18.3")
    jackson("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    jackson("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")

    shadow("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    shadow(kotlin("stdlib"))
    shadow(kotlin("reflect"))

    shadow("commons-io:commons-io:2.18.0")

    shadow("io.ktor:ktor-client-cio:3.0.3")

    objectContainer(configurationName = "testImplementation")

    testImplementation(project(":api"))
    extLoader(configurationName = "testImplementation")
    testImplementation(kotlin("test"))
    boot(configurationName = "testImplementation")
    artifactResolver(configurationName = "testImplementation", maven = true)
    commonUtil(configurationName = "testImplementation")
    toolingApi(configurationName = "testImplementation")
}

val listAllDependencies by tasks.registering(ListAllDependencies::class)

tasks.shadowJar {
    configurations = listOf(jacksonConfig)
    archiveClassifier.set("")

    from(listAllDependencies)
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:.*:.*"))
    }
    enableRelocation = true
    relocationPrefix = "dev.extframework.gradle.internal"
}

gradlePlugin {
    website = "https://github.com/extframework"
    vcsUrl = "https://github.com/extframework/extframework-gradle-plugin"
    plugins {
        create("extframework") {
            id = "dev.extframework"
            implementationClass = "dev.extframework.gradle.ExtframeworkPlugin"
            displayName = "Extframework Gradle plugin"
            description = "Extframework Gradle plugin"
        }
    }
}

tasks.jar {
    isEnabled = false
}

common {
    defaultJavaSettings()
    publishing {
        repositories {
            extFramework(credentials = propertyCredentialProvider, type = RepositoryType.RELEASES)
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            artifactId = "gradle-plugin"
        }
    }
}

kotlin {
    jvmToolchain(8)
}

abstract class ListAllDependencies : DefaultTask() {
    init {
        // Define the output file within the build directory
        val outputFile = project.buildDir.resolve("generated/main/dependencies.txt")
        outputs.file(outputFile)
    }

    @TaskAction
    fun listDependencies() {
        val outputFile = project.buildDir.resolve("generated/main/dependencies.txt")
        // Ensure the directory for the output file exists
        outputFile.parentFile.mkdirs()
        // Clear or create the output file
        outputFile.writeText("")

        val set = HashSet<String>()

        // Process each configuration that can be resolved
        listOf(
            project.configurations.named("shadow"),
            project.configurations.named("compileClasspath"),
            project.configurations.named("compileClasspath"),
        )
            .map { it.get() }
            .filter { it.isCanBeResolved }
            .forEach { configuration ->
                println("Processing configuration: ${configuration.name}")
                try {
                    configuration.resolvedConfiguration.firstLevelModuleDependencies.forEach { dependency ->
                        collectDependencies(dependency, set)
                    }
                } catch (e: Exception) {
                    println("Skipping configuration '${configuration.name}' due to resolution errors.")
                }
            }

        // Resolved dependencies from gradle do not have local artifact IDs correct
        set.add("dev.extframework:gradle-api:1")

        set.forEach {
            outputFile.appendText("$it\n")
        }
    }

    private fun collectDependencies(dependency: ResolvedDependency, set: MutableSet<String>) {
        dependency.children.forEach { childDependency ->
            collectDependencies(childDependency, set)
        }

//        if (dependency is ProjectDependency) {
//            val maven = dependency.dependencyProject.publishing.publications
//                .filterIsInstance<MavenPublication>()
//                .firstOrNull()
//
//            if (maven != null) {
//                set.add("${maven.groupId}:${maven.artifactId}:${maven.version}}")
//
//                return
//            }
//        }

        set.add("${dependency.moduleGroup}:${dependency.moduleName}:${dependency.moduleVersion}")
    }
}
