package dev.extframework.gradle

import dev.extframework.common.util.resolve
import dev.extframework.gradle.deobf.MinecraftMappings
import dev.extframework.gradle.tasks.GenerateMcSources
import dev.extframework.gradle.tasks.registerGenerateErmTask
import dev.extframework.gradle.tasks.registerLaunchTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.jvm.tasks.Jar
import java.net.URI
import java.nio.file.Path

internal const val CLIENT_VERSION = "2.1.1-SNAPSHOT"
internal const val CLIENT_MAIN_CLASS = "dev.extframework.client.MainKt"
internal const val CORE_MC_VERSION = "1.0.4-SNAPSHOT"

internal val HOME_DIR = Path.of(System.getProperty("user.home")) resolve ".extframework"

class ExtFrameworkPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        MinecraftMappings.setup(project.layout.projectDirectory.file("mappings").asFile.toPath())

        project.plugins.apply(JvmEcosystemPlugin::class.java)
        val extframework = project.extensions.create("extension", ExtFrameworkExtension::class.java, project)

        val generateErm = project.registerGenerateErmTask()

        project.tasks.named("jar", Jar::class.java) { jar ->
            jar.dependsOn(generateErm)
            jar.dependsOn(project.tasks.withType(GenerateMcSources::class.java))
        }

        project.registerLaunchTask(extframework, project.tasks.getByName("publishToMavenLocal"))

        project.tasks.register("genMcSources") {
            it.dependsOn(project.tasks.withType(GenerateMcSources::class.java))
        }
    }
}

fun RepositoryHandler.extframework() {
    maven {
        it.url = URI.create("https://maven.extframework.dev/snapshots")
    }
}


