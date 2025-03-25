package dev.extframework.gradle.api

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

public interface ExtframeworkExtension {
    public val project: Project
    public val worker: ExtensionWorker
    public val configuration: ExtensionConfig
    public val model: MutableExtensionRuntimeModel
    public val sourceSets: SourceSetContainer
    public val partitions : NamedDomainPartitionContainer
    public val metadata: MutableExtensionMetadata


    public fun partitions(action: Action<NamedDomainPartitionContainer>)

    public fun model(action: Action<MutableExtensionRuntimeModel>)

    public fun metadata(action: Action<MutableExtensionMetadata>)

    public fun initialize()
}