import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import org.gradle.kotlin.dsl.provideDelegate
import org.jetbrains.kotlin.gradle.utils.extendsFrom

plugins {
    `java-gradle-plugin`
    kotlin("jvm")

    id("com.gradle.plugin-publish")
    id("dev.extframework.common")
    id("com.gradleup.shadow") version "9.0.0-beta10"
}

group = "dev.extframework.tools"
version = "1.3.0"

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
    // TODO this is not correct, also 2.12.3 has issues, but required to run rn
    constraints {
        implementation("com.fasterxml.jackson.core:jackson-core") {
            version {
                strictly("2.18.3")
            }
        }
    }

    boot(configurationName = "shadow", version = "3.6.2-SNAPSHOT")
    extLoader(configurationName = "shadow",version = "2.1.17-SNAPSHOT")
    toolingApi(configurationName = "shadow",version = "1.0.8-SNAPSHOT")

    artifactResolver(configurationName = "shadow",maven = true)
    archiveMapper(configurationName = "shadow",transform = true, tiny = true, proguard = true, mcpLegacy = true)
    launcherMetaHandler(configurationName = "shadow",)
    archives(configurationName = "shadow",)
    commonUtil(configurationName = "shadow",)
    toolingApi(configurationName = "shadow",)
    objectContainer(configurationName = "shadow",)
    jobs(configurationName = "shadow")

    shadow(project(":gradle-api"))
    jackson("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.18.3")
    jackson("com.fasterxml.jackson.core:jackson-core:2.18.3")
    jackson("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    jackson("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")

    shadow("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    shadow(kotlin("stdlib"))
    shadow(kotlin("reflect"))

    shadow("commons-io:commons-io:2.18.0")


    testImplementation(kotlin("test"))
    commonUtil(configurationName = "testImplementation")
    toolingApi(configurationName = "testImplementation",version = "1.0.8-SNAPSHOT")
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
//    dependsOn(tasks.shadowJar)
//    from(tasks.shadowJar.get().archiveFile.get().asFile)
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
        project.configurations.filter { it.isCanBeResolved }.forEach { configuration ->
            println("Processing configuration: ${configuration.name}")
            try {
                configuration.resolvedConfiguration.firstLevelModuleDependencies.forEach { dependency ->
                    collectDependencies(dependency, set)
                }
            } catch (e: Exception) {
                println("Skipping configuration '${configuration.name}' due to resolution errors.")
            }
        }

        set.add("${this.project.group}:minecraft-bootstrapper:${this.project.version}\n")

        set.forEach {
            outputFile.appendText(it)
        }
    }

    private fun collectDependencies(dependency: ResolvedDependency, set: MutableSet<String>) {
        set.add("${dependency.moduleGroup}:${dependency.moduleName}:${dependency.moduleVersion}\n")
        dependency.children.forEach { childDependency ->
            collectDependencies(childDependency, set)
        }
    }
}
