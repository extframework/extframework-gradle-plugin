package dev.extframework.gradle.partition

import dev.extframework.tooling.api.extension.partition.ExtensionPartitionMetadata

public data class GradlePartitionMetadata(
    val entrypoint: String
) : ExtensionPartitionMetadata {
    override val name: String = "gradle"
}