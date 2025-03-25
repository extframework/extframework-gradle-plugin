package dev.extframework.gradle.api.util

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import org.gradle.api.artifacts.Dependency

internal fun ermDependency(dependency: Dependency): Map<String, String>? {
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
