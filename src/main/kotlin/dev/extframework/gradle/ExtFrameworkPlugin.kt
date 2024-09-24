package dev.extframework.gradle

import dev.extframework.common.util.resolve
import dev.extframework.gradle.deobf.MinecraftMappings
import dev.extframework.gradle.publish.DefaultExtensionPublication
import dev.extframework.gradle.publish.ExtensionPublication
import dev.extframework.gradle.publish.ExtensionPublishTask
import dev.extframework.gradle.tasks.BuildBundle
import dev.extframework.gradle.tasks.GenerateMcSources
import dev.extframework.gradle.tasks.registerLaunchTask
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
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

        val bundleTask = project.tasks.register("bundle", BuildBundle::class.java) {
            it.dependsOn(project.provider {
                extframework.partitions.flatMap { p ->
                    listOf(
                        project.tasks.getByName(p.sourceSet.jarTaskName),
                        project.tasks.getByName(p.generatePrmTaskName)
                    )
                }
            })
        }

        project.extensions.getByType(PublishingExtension::class.java).publications.registerFactory(
            ExtensionPublication::class.java
        ) { name ->
            DefaultExtensionPublication(
                name
            )
        }
        val publishTask = project.tasks.register("publishExtension", ExtensionPublishTask::class.java) {
            it.dependsOn(bundleTask)

            it.bundle.set(bundleTask.map(BuildBundle::bundlePath))
        }

        project.tasks.named("jar", Jar::class.java) { jar ->
            jar.dependsOn(project.tasks.withType(GenerateMcSources::class.java))
        }

        project.registerLaunchTask(extframework, publishTask.get())

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


