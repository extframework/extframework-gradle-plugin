package dev.extframework.gradle.api.util

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

internal fun ermDependency(dependency: Dependency): Map<String, String>? {
    if (dependency is ProjectDependency) {
        val publishing = dependency.dependencyProject.extensions.findByType(PublishingExtension::class.java)

        if (publishing != null) {
            val all = publishing.publications
                .filterIsInstance<MavenPublication>()
                .map {
                    ermDependency(
                        "${it.groupId}:${it.artifactId}:${it.version}"
                    )
                }

            // TODO standardize this behavior of throwing error when conflicting publications are found. What does gradle itself do?
            check(all.all { it == all.first() }) { "Found multiple conflicting publications for project: '${dependency.dependencyProject.name}'" }

            if (all.isNotEmpty()) return all.first()
        }
    }

    return ermDependency(
        "${dependency.group}:${dependency.name}:${dependency.version}"
    )
}

internal fun ermDependency(notation: String): Map<String, String>? {
    val dependency = SimpleMavenDescriptor.parseDescription(notation) ?: return null

    if (dependency.group.isBlank() ||
        dependency.artifact == "unspecified" ||
        dependency.version.isBlank()
    ) return null

    return mapOf( // Always a good idea to fill out default values even if they are provided just in case libraries update.
        "descriptor" to ("${dependency.group}:${dependency.artifact}:${dependency.version}"),
        "isTransitive" to "true",
        "includeScopes" to "compile,runtime,import",
        "excludeArtifacts" to ""
    )
}
