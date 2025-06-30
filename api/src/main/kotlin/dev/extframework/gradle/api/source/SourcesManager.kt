package dev.extframework.gradle.api.source

import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.extension.partition.PartitionResolver

public interface SourcesManager : ExtensionEnvironment.Attribute {
    override val key: ExtensionEnvironment.Attribute.Key<*>
        get() = SourcesManager
    public val graph: ArchiveGraph
    public val partitionResolver: PartitionResolver

    public companion object : ExtensionEnvironment.Attribute.Key<SourcesManager>
}