import com.kaolinmc.gradle.common.*

plugins {
    `java-gradle-plugin`
    kotlin("jvm")

    id("com.gradle.plugin-publish")
    id("com.kaolinmc.common")
}

group = "com.kaolinmc"
version = "0.1.2"

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
    implementation(archives())
    implementation(commonUtil())
    implementation(objectContainer())
    implementation(resourceApi())

    implementation(project(":api"))

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.19.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.19.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("commons-io:commons-io:2.18.0")
    implementation("io.ktor:ktor-client-cio:3.0.3")

    testImplementation(objectContainer())
    testImplementation(project(":api"))
    testImplementation(extLoader())
    testImplementation(kotlin("test"))
    testImplementation(boot())
    testImplementation(artifactResolver())
    testImplementation(commonUtil())
    testImplementation(toolingApi())
}

val listAllDependencies by tasks.registering(ListAllDependencies::class)

gradlePlugin {
    website = "https://github.com/kaolinmc"
    vcsUrl = "https://github.com/kaolinmc/kiln"
    plugins {
        create("kaolin") {
            id = "kaolin.kiln"
            implementationClass = "com.kaolinmc.kiln.KaolinKiln"
            displayName = "Kaolin Kiln"
            description = "Kaolin Kiln"
        }
    }
}

tasks.jar {
    from(listAllDependencies)
}

common {
    defaultJavaSettings()
    publishing {
        repositories {
            kaolin(credentials = propertyCredentialProvider, type = RepositoryType.RELEASES)
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            artifactId = "kiln"
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
//            project.configurations.named("shadow"),
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
        set.add("com.kaolinmc:gradle-api:1")

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
