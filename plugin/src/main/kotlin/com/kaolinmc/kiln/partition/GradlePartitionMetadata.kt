package com.kaolinmc.kiln.partition

import com.kaolinmc.tooling.api.extension.partition.ExtensionPartitionMetadata

public data class GradlePartitionMetadata(
    val entrypoint: String
) : ExtensionPartitionMetadata {
    override val name: String = "gradle"
}