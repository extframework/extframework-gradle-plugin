package com.kaolinmc.kiln.api

import com.kaolinmc.tooling.api.extension.ExtensionNode
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

public interface KaolinExtension {
    public val worker: EnvironmentInitializer
    public val project: Project

    public val configuration: ExtensionConfig
    public val model: MutableExtensionRuntimeModel
    public val metadata: MutableExtensionMetadata

    public val sourceSets: SourceSetContainer
    public val partitions: NamedDomainPartitionContainer

    public val rootEnvironment: BuildEnvironment
    public val environments: MutableList<BuildEnvironment>

    public val build: BuildCache

    public val finalizationActions: List<Action<KaolinExtension>>

    public fun finalizedBy(action: Action<KaolinExtension>)

    public fun partitions(action: Action<NamedDomainPartitionContainer>)

    public fun model(action: Action<MutableExtensionRuntimeModel>)

    public fun metadata(action: Action<MutableExtensionMetadata>)

    public data class BuildCache(
        public val parents: MutableList<ParentMetadata>,
        public val fingerprint: MutableList<String>,
        public val content: MutableList<String>
    ) {
        public data class ParentMetadata(
            public val node: ExtensionNode,
            public val pluginName: String?,
            public val tweakerName: String?,
        )
    }
}