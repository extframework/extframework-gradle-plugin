package com.kaolinmc.kiln.api

import com.kaolinmc.kiln.api.util.ermDependency
import com.kaolinmc.tooling.api.extension.ExtensionParent
import com.kaolinmc.tooling.api.extension.ExtensionRepository
import com.kaolinmc.tooling.api.extension.ExtensionRuntimeModel
import com.kaolinmc.tooling.api.extension.PartitionRuntimeModel
import com.kaolinmc.tooling.api.extension.artifact.ExtensionDescriptor
import org.gradle.api.Action
import org.gradle.api.artifacts.Dependency
import org.gradle.api.provider.*

public data class MutableExtensionRuntimeModel(
    val apiVersion: Int,
    val groupId: Provider<String>,
    val name: Property<String>,
    val version: Provider<String>,

    val repositories: ListProperty<Map<String, String>>,
    val parents: SetProperty<ExtensionParent>,

    val partitions: SetProperty<MutablePartitionRuntimeModel>,
    val attributes: MapProperty<String, String>,
) {
    public fun partitions(action: Action<MutablePartitionRuntimeModel>) {
        (partitions.orNull ?: emptySet()).forEach { partition ->
            action.execute(partition)
        }
    }

    public fun partition(name: String, action: Action<MutablePartitionRuntimeModel>) {
        partitions.get().find { it.name == name }?.let { action.execute(it) }
    }

    public fun attribute(key: String, value: Any) {
        attributes.put(key, value.toString())
    }

    public fun toImmutable() : ExtensionRuntimeModel {
        return ExtensionRuntimeModel(
            apiVersion,
            groupId.get(),
            name.get(),
            version.get(),
            repositories.get(),
            parents.get(),
            partitions.get().mapTo(HashSet()) {
                it.toImmutable()
            },
            attributes.get(),
        )
    }
}

public val MutableExtensionRuntimeModel.descriptor : ExtensionDescriptor
    get() = ExtensionDescriptor.parseDescriptor("${groupId.get()}:${name.get()}:${version.get()}")

public data class MutablePartitionRuntimeModel(
    val type: String,

    val name: String,

    val repositories: ListProperty<ExtensionRepository>,
    val dependencies: SetProperty<EvaluatingDependency>,

    val options: MapProperty<String, String>
) {
    public fun toImmutable() : PartitionRuntimeModel {
        return PartitionRuntimeModel(
            type,
            name,
            repositories.get(),
            dependencies.get().mapNotNullTo(HashSet()) {
                it.evaluate()
            },
            options.get()
        )
    }
}

public sealed interface EvaluatingDependency {
    public fun evaluate() : Map<String, String>?

    public data class Raw(
        val contents: Map<String, String>
    ) : EvaluatingDependency {
        override fun evaluate(): Map<String, String> {
            return contents
        }
    }

    public data class GradleArtifact(
        val artifact: Dependency
    ) : EvaluatingDependency {
        override fun evaluate(): Map<String, String>? {
            return ermDependency(artifact)
        }
    }
}