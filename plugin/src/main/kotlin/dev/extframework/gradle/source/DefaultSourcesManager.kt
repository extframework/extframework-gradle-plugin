package dev.extframework.gradle.source

import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.extloader.ArchiveGraphView
import dev.extframework.gradle.api.source.SourcesManager
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.extension.partition.PartitionResolver

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