package dev.extframework.gradle

import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

fun MavenPublication.withExtension(project: Project) {
    artifact(project.tasks.named("generateErm")).classifier = "erm"

    project.extensions.getByType(ExtFrameworkExtension::class.java).partitions.forEach {
        artifact(project.tasks.named(it.sourceSet.jarTaskName)).classifier = it.name
        artifact(project.tasks.named(it.generatePrmTaskName)).classifier = it.name
    }
}