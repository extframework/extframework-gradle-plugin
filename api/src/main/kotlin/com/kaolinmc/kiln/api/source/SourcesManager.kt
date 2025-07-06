package com.kaolinmc.kiln.api.source

import com.kaolinmc.boot.archive.ArchiveGraph
import com.kaolinmc.tooling.api.environment.ExtensionEnvironment
import com.kaolinmc.tooling.api.extension.partition.PartitionResolver

public interface SourcesManager : ExtensionEnvironment.Attribute {
    override val key: ExtensionEnvironment.Attribute.Key<*>
        get() = SourcesManager
    public val graph: ArchiveGraph
    public val partitionResolver: PartitionResolver

    public companion object : ExtensionEnvironment.Attribute.Key<SourcesManager>
}