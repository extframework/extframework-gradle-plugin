package com.kaolinmc.kiln.source

import com.kaolinmc.boot.archive.ArchiveGraph
import com.kaolinmc.extloader.ArchiveGraphView
import com.kaolinmc.kiln.api.source.SourcesManager
import com.kaolinmc.tooling.api.environment.ExtensionEnvironment
import com.kaolinmc.tooling.api.extension.partition.PartitionResolver

open class DefaultSourcesManager internal constructor(
    override val graph: ArchiveGraph,
    override val partitionResolver: PartitionResolver
) : SourcesManager {
    override fun compose(into: ExtensionEnvironment): ExtensionEnvironment.Attribute.View<*>? {
        return View(this, into)
    }

    private class View(
        override var reference: SourcesManager,
        environment: ExtensionEnvironment
    ) : ExtensionEnvironment.Attribute.View<SourcesManager>, DefaultSourcesManager(
        ArchiveGraphView { reference.graph },
        SourcePartitionResolver(environment)
    ) {
        override var isValid: Boolean = true

        init {
            graph.resolvers.register(partitionResolver)
        }
    }
}