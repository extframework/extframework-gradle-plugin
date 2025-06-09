package dev.extframework.gradle.api.source

import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.`object`.ObjectContainerImpl
import dev.extframework.tooling.api.environment.ExtensionEnvironment

public class SourceDependencyTypeContainer (
    private val archiveGraph: ArchiveGraph
): ObjectContainerImpl<DependencySourceProvider<*>>(), ExtensionEnvironment.Attribute {
    override val key: ExtensionEnvironment.Attribute.Key<*>
        get() = SourceDependencyTypeContainer

    override fun register(name: String, provider: DependencySourceProvider<*>): Boolean {
        archiveGraph.registerResolver(provider.resolver)
        return super.register(name, provider)
    }

    public companion object : ExtensionEnvironment.Attribute.Key<SourceDependencyTypeContainer>
}