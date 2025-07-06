package com.kaolinmc.kiln.util

import com.kaolinmc.kiln.api.KaolinExtension
import com.kaolinmc.kiln.api.source.SourcesManager
import com.kaolinmc.kiln.publish.DefaultExtensionPublication
import com.kaolinmc.kiln.publish.ExtensionPublication
import com.kaolinmc.kiln.publish.registerPublishExtensionToLocalTask
import com.kaolinmc.kiln.tasks.BuildBundle
import com.kaolinmc.kiln.tasks.ExtensionPublishTask
import com.kaolinmc.kiln.tasks.GenerateErm
import com.kaolinmc.tooling.api.ExtensionLoader
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin


internal fun setupProject(
    extension: KaolinExtension,
) {
    val project = extension.project

    project.plugins.apply(MavenPublishPlugin::class.java)
    project.plugins.apply(JvmEcosystemPlugin::class.java)

    project.tasks.register("generateErm", GenerateErm::class.java) {
        it.outputs.upToDateWhen { false }
    }

    val bundleTask = project.tasks.register("bundle", BuildBundle::class.java) {
        it.dependsOn(project.provider {
            extension.partitions.flatMap { p ->
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

    registerPublishExtensionToLocalTask(extension)

    // Add flat dir so we can add sources / classes
    project.repositories.flatDir {
        it.dirs(
            extension.rootEnvironment[SourcesManager].graph.path.toString(),
            extension.rootEnvironment[ExtensionLoader].graph.path.toString()
        )
    }
}