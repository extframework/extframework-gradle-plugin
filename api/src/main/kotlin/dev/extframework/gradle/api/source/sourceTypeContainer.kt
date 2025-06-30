package dev.extframework.gradle.api.source

import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.dependency.DependencyResolverProvider
import dev.extframework.`object`.ObjectContainerImpl
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.environment.ObjectContainerAttribute
import dev.extframework.tooling.api.environment.dependencyTypesAttrKey

public val sourceDependencyTypesAttrKey: ObjectContainerAttribute.Key<DependencySourceProvider<*>> =
    ObjectContainerAttribute.Key("source-dependency-types")
//
//public class SourceDependencyTypeContainer (
//    private val archiveGraph: ArchiveGraph
//): ObjectContainerImpl<DependencySourceProvider<*>>(), ExtensionEnvironment.Attribute {
//    override val key: ExtensionEnvironment.Attribute.Key<*>
//        get() = SourceDependencyTypeContainer
//
//    override fun register(name: String, provider: DependencySourceProvider<*>): Boolean {
//        archiveGraph.registerResolver(provider.resolver)
//        dependencyTypesAttrKey.register(name, provider)
//        return super.register(name, provider)
//    }
//
//    public companion object : ExtensionEnvironment.Attribute.Key<SourceDependencyTypeContainer>
//}