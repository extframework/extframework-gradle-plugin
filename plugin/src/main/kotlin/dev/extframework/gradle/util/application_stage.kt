package dev.extframework.gradle.util

import dev.extframework.gradle.api.ExtframeworkExtension
import dev.extframework.gradle.publish.DefaultExtensionPublication
import dev.extframework.gradle.publish.ExtensionPublication
import dev.extframework.gradle.publish.registerPublishExtensionToLocalTask
import dev.extframework.gradle.tasks.BuildBundle
import dev.extframework.gradle.tasks.ExtensionPublishTask
import dev.extframework.gradle.tasks.GenerateErm
import org.gradle.api.Project
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin


internal fun setupProject(
    extframework: ExtframeworkExtension,
) {
    val project = extframework.project

    project.plugins.apply(MavenPublishPlugin::class.java)
    project.plugins.apply(JvmEcosystemPlugin::class.java)

    project.tasks.register("generateErm", GenerateErm::class.java)

    val bundleTask = project.tasks.register("bundle", BuildBundle::class.java) {
        it.dependsOn(project.provider {
            extframework.partitions.flatMap { p ->
                listOf(
                    project.tasks.getByName(p.sourceSet.jarTaskName),
                    project.tasks.getByName(p.sourceSet.sourcesJarTaskName),
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

    // Add flat dir so we can add sources / classes
    project.repositories.flatDir {
        it.dirs(extframework.sourcesGraph.path.toString(), extframework.loader.graph.path.toString())
    }
}