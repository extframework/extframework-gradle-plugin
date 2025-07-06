package com.kaolinmc.kiln

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.kaolinmc.boot.archive.ArchiveGraph
import com.kaolinmc.boot.archive.ArchiveTreeAuditContext
import com.kaolinmc.boot.archive.ArchiveTreeAuditor
import com.kaolinmc.boot.dependency.DependencyTypeContainer
import com.kaolinmc.boot.maven.MavenConstraintNegotiator
import com.kaolinmc.boot.maven.MavenResolverProvider
import com.kaolinmc.boot.monad.removeIf
import com.kaolinmc.common.util.readInputStream
import com.kaolinmc.common.util.resolve
import com.kaolinmc.common.util.runCatching
import com.kaolinmc.extloader.ArchiveGraphView
import com.kaolinmc.extloader.DefaultExtensionLoader
//import com.kaolinmc.extloader.RootExtensionEnvironment
import com.kaolinmc.extloader.extension.DefaultExtensionResolver
import com.kaolinmc.extloader.extension.ExtensionLayerClassLoader
import com.kaolinmc.extloader.extension.partition.DefaultPartitionResolver
import com.kaolinmc.kiln.api.KaolinExtension
import com.kaolinmc.kiln.api.descriptor
import com.kaolinmc.`object`.ObjectContainerImpl
import com.kaolinmc.tooling.api.ExtensionLoader
//import com.kaolinmc.tooling.api.environment.EnvironmentRegistry
import com.kaolinmc.tooling.api.environment.ExtensionEnvironment
import com.kaolinmc.tooling.api.environment.ObjectContainerAttribute
import com.kaolinmc.tooling.api.environment.SetView
import com.kaolinmc.tooling.api.environment.ValueAttribute
import com.kaolinmc.tooling.api.environment.dependencyTypesAttrKey
import com.kaolinmc.tooling.api.environment.wrkDirAttrKey
import com.kaolinmc.tooling.api.extension.ExtensionClassLoader
import com.kaolinmc.tooling.api.extension.ExtensionNode
import com.kaolinmc.tooling.api.extension.ExtensionResolver
import com.kaolinmc.tooling.api.extension.ExtensionRuntimeModel
import com.kaolinmc.tooling.api.extension.artifact.ExtensionDescriptor
import com.kaolinmc.tooling.api.extension.artifact.ExtensionRepositorySettings
import com.kaolinmc.tooling.api.extension.partition.artifact.PartitionDescriptor
import com.kaolinmc.tooling.api.tweaker.EnvironmentTweaker
import java.nio.file.Path
import kotlin.io.path.Path

internal fun ExtensionLoader(
    path: Path,
    owner: KaolinExtension
): ExtensionLoader {
    val environment by owner::rootEnvironment

    val graph = ClassesArchiveGraph(path resolve "archives"); auditors(graph)

    val dependencyTypes: DependencyTypeContainer = ObjectContainerImpl()
    val maven = MavenResolverProvider()

    dependencyTypes.register(maven)
    graph.resolvers.register(maven.resolver)

    environment += ObjectContainerAttribute(dependencyTypesAttrKey, dependencyTypes)
    environment += ValueAttribute(wrkDirAttrKey, path)

    val resolver = GradleExtensionResolver(
        { desc ->
            if (!owner.worker.bootstrapped) false
            else owner.worker.managed.any {
                it.model.descriptor == desc
            }
        },
        owner.worker.mock.archives,
        owner.project.buildscript.classLoader,
        environment,
    )

    return GradleExtensionLoader(resolver, graph, owner, owner.rootEnvironment)
}

private open class GradleExtensionLoader(
    override val extensionResolver: GradleExtensionResolver,
    graph: ArchiveGraph,
    private val extension: KaolinExtension,
    environment: ExtensionEnvironment
) : DefaultExtensionLoader(extensionResolver, graph, environment) {
    protected open val tweaked: MutableSet<ExtensionDescriptor> = HashSet()

    override suspend fun tweak(
        extensions: List<ExtensionNode>,
    ) {
        val extensionDescriptors = extensions.mapTo(HashSet()) { it.descriptor }
        // This is copied from the TweakerPartitionLoader which is just messy, there should be a better way to do this.
        val tweakers = extension.build.parents
            .filter { extensionDescriptors.contains(it.node.descriptor) }
            .filter { tweaked.add(it.node.descriptor) }
            .mapNotNull { it.tweakerName }
            .map {
                runCatching(ClassNotFoundException::class) {
                    extension.project.buildscript.classLoader.loadClass(it)
                } ?: throw IllegalArgumentException(
                    "Could not load tweaker partition because the class: '${it}' couldn't be found."
                )
            }.map { tweakerClass ->
                val extensionConstructor =
                    runCatching(NoSuchMethodException::class) { tweakerClass.getConstructor() }
                        ?: throw IllegalArgumentException("Could not find no-arg constructor in class: '${tweakerClass}' in tweaker partition.")

                val instance = extensionConstructor.newInstance() as? EnvironmentTweaker
                    ?: throw IllegalArgumentException("Tweaker class: '${tweakerClass}' does not implement: '${EnvironmentTweaker::class.qualifiedName}'.")

                instance
            }

        tweakers.forEach {
            it.tweak(environment)
        }
    }

    override fun compose(into: ExtensionEnvironment): ExtensionEnvironment.Attribute.View<*> {
        return View(this, into)
    }

    private class View(
        override var reference: GradleExtensionLoader,
        environment: ExtensionEnvironment
    ) : ExtensionEnvironment.Attribute.View<GradleExtensionLoader>, GradleExtensionLoader(
        GradleExtensionResolver.View(
            { reference.extensionResolver },
            environment
        ),
        ArchiveGraphView { reference.graph },
        reference.extension,
        environment
    ) {
        override var isValid: Boolean = true
        override val key: ExtensionEnvironment.Attribute.Key<*> = ExtensionLoader

        override val tweaked: MutableSet<ExtensionDescriptor> = SetView {
            reference.tweaked
        }
    }
}

/**
 * Oddities in how we do auditors for gradle.
 *
 * The only archive that should ever be loaded using this system is that of
 * Gradle partitions and extension tweakers. However, Gradle partitions by nature
 * will load the tweakers (most of the time**) and for this reason all Gradle and
 * most tweaker partitions are classloader by Gradle itself meaning it should not
 * be included in the archive tree here.
 *
 * We specifically choose not to remove prepackaged dependencies from the tree because
 * we want to have information about those during caching.
 *
 * TODO: An alternative to this approach is to specifically load all tweakers / gradle
 *  partitions during the creation of the build cache. This would mean that nothing ever
 *  gets strictly loaded during a configuration situation and we would not have to include
 *  this code. (but would need to add back the pre packaged dependency remover)
 */
private fun auditors(graph: ArchiveGraph) {
    val negotiator = MavenConstraintNegotiator()

    val alreadyLoaded = parsePackagedDependencies().mapTo(HashSet()) {
        negotiator.classify(it)
    }

    val packagedDependencyRemover = object : ArchiveTreeAuditor {
        override fun audit(event: ArchiveTreeAuditContext) = event.copy(tree = event.tree.removeIf {
            alreadyLoaded.contains(
                negotiator.classify(
                    it.value.descriptor as? SimpleMavenDescriptor ?: return@removeIf false
                )
            )
        }!!)
    }

    graph.auditors = graph.auditors.chain(packagedDependencyRemover)
}

private fun parsePackagedDependencies(): Set<SimpleMavenDescriptor> {
    val dependencies: java.util.HashSet<SimpleMavenDescriptor> =
        KaolinKiln::class.java.getResourceAsStream("/dependencies.txt")?.use {
            val fileStr = String(it.readInputStream())
            fileStr.split("\n").toSet()
        }?.filterNot { it.isBlank() }?.mapTo(HashSet()) { SimpleMavenDescriptor.parseDescription(it)!! }
            ?: throw IllegalStateException("Cant load dependencies?")

    return dependencies
}

internal open class GradleExtensionResolver(
    val isMocked: (ExtensionDescriptor) -> Boolean,
    val mockBasePath: Path,
    classloader: ClassLoader,
    environment: ExtensionEnvironment,

//    environmentRegistry: EnvironmentRegistry,
//    defaultEnvironment: String,
) : DefaultExtensionResolver(
    classloader, environment
) {
    override val partitionResolver: DefaultPartitionResolver = object : DefaultPartitionResolver(
        accessBridge, environment//environmentRegistry, defaultEnvironment
    ) {
        override fun pathForDescriptor(descriptor: PartitionDescriptor, classifier: String, type: String): Path {
            val basePath = if (isMocked(descriptor.extension)) {
                mockBasePath
            } else Path("extensions")

            return basePath resolve super.pathForDescriptor(descriptor, classifier, type)
        }
    }

    override fun pathForDescriptor(descriptor: ExtensionDescriptor, classifier: String, type: String): Path {
        val basePath = if (isMocked(descriptor)) {
            mockBasePath
        } else Path("extensions")

        return basePath resolve super.pathForDescriptor(descriptor, classifier, type)
    }

    internal class View(
        private val _reference: () -> GradleExtensionResolver,
        environment: ExtensionEnvironment
    ) : GradleExtensionResolver(
        _reference().isMocked,
        _reference().mockBasePath,
        ClassLoader.getSystemClassLoader(),
        environment
    ) {
        private val reference: ExtensionResolver
            get() = _reference()
        override val layerLoader: ExtensionLayerClassLoader = ExtensionLayerClassLoader(
            reference.layerLoader,
            "Extension Layer ${environment.name}"
        )

        // TODO A bug or unfortunate feature in kotlin forces us to have to do this (instead of just
        //    overriding the property). This should be reported eventually (check out the java decomp
        //    to see whats wrong)
        private var _accessBridge: ExtensionResolver.AccessBridge? = null
        override val accessBridge: ExtensionResolver.AccessBridge
            get() {
                if (_accessBridge == null) {
                    _accessBridge = object : ExtensionResolver.AccessBridge {
                        override fun classLoaderFor(descriptor: ExtensionDescriptor): ExtensionClassLoader {
                            return (extensionClassloaders[descriptor.toIdentifier()])
                                ?: reference.accessBridge.classLoaderFor(
                                    descriptor
                                )
                        }

                        override fun ermFor(descriptor: ExtensionDescriptor): ExtensionRuntimeModel {
                            return extensionMetadata[descriptor.toIdentifier()]?.erm ?: reference.accessBridge.ermFor(
                                descriptor
                            )
                        }

                        override fun repositoryFor(descriptor: ExtensionDescriptor): ExtensionRepositorySettings {
                            return extensionMetadata[descriptor.toIdentifier()]?.repository
                                ?: reference.accessBridge.repositoryFor(
                                    descriptor
                                )
                        }
                    }
                }

                return _accessBridge!!
            }
    }
}