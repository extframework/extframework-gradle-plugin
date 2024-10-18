package dev.extframework.gradle

import GenerateErm
import dev.extframework.gradle.deobf.MinecraftMappings
import dev.extframework.gradle.publish.DefaultExtensionPublication
import dev.extframework.gradle.publish.ExtensionPublication
import dev.extframework.gradle.publish.ExtensionPublishTask
import dev.extframework.gradle.publish.registerPublishExtensionToLocalTask
import dev.extframework.gradle.tasks.BuildBundle
import dev.extframework.gradle.tasks.GenerateMcSources
import dev.extframework.gradle.tasks.registerLaunchTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import registerGenerateErmTask
import java.net.URI

internal const val CLIENT_VERSION = "1.0.3-BETA"
internal const val CLIENT_MAIN_CLASS = "dev.extframework.client.MainKt"
internal const val CORE_MC_VERSION = "1.0.8-BETA"

class ExtFrameworkPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        MinecraftMappings.setup(project.layout.projectDirectory.file("mappings").asFile.toPath())

        project.plugins.apply(MavenPublishPlugin::class.java)
        project.plugins.apply(JvmEcosystemPlugin::class.java)
        val extframework = project.extensions.create("extension", ExtFrameworkExtension::class.java, project)

        project.registerGenerateErmTask()
        val bundleTask = project.tasks.register("bundle", BuildBundle::class.java) {
            it.dependsOn(project.provider {
                extframework.partitions.flatMap { p ->
                    listOf(
                        project.tasks.getByName(p.sourceSet.jarTaskName),
                        project.tasks.getByName(p.generatePrmTaskName)
                    )
                }
            })
            it.dependsOn(project.tasks.withType(GenerateErm::class.java))
        }

        project.extensions.getByType(PublishingExtension::class.java).publications.registerFactory(
            ExtensionPublication::class.java
        ) { name ->
            DefaultExtensionPublication(
                name
            )
        }

        project.tasks.register("publishExtension", ExtensionPublishTask::class.java) {
            it.dependsOn(bundleTask)

            it.bundle.set(bundleTask.map(BuildBundle::bundlePath))
        }

        registerPublishExtensionToLocalTask(extframework)

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


