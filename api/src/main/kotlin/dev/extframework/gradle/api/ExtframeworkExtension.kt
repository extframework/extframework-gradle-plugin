package dev.extframework.gradle.api

import dev.extframework.tooling.api.extension.ExtensionNode
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

public interface ExtframeworkExtension {
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

    public val finalizationActions: List<Action<ExtframeworkExtension>>

    public fun finalizedBy(action: Action<ExtframeworkExtension>)

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