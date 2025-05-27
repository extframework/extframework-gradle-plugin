package dev.extframework.gradle.publish

import dev.extframework.gradle.api.ExtframeworkExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

fun registerPublishExtensionToLocalTask(
    extension: ExtframeworkExtension,
) {
    val project = extension.project
    val tasks = project.tasks

    val publish = project.extensions.getByType(PublishingExtension::class.java)

    publish.publications.register("local-${project.name}", MavenPublication::class.java) { pub ->
        project.afterEvaluate {
            pub.artifactId = extension.model.name.get()
            for (partition in extension.partitions) {
                pub.artifact(tasks.named(partition.sourceSet.jarTaskName)).classifier = partition.name
                pub.artifact(tasks.named(partition.sourceSet.sourcesJarTaskName)).classifier = partition.name + "-sources"
            }
        }

        pub.artifact(project.tasks.named("generateErm")).classifier = "erm"
    }
}