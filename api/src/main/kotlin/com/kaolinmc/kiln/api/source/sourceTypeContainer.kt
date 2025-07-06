package com.kaolinmc.kiln.api.source

import com.kaolinmc.tooling.api.environment.ObjectContainerAttribute

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