package dev.extframework.gradle

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.archive.ArchiveTreeAuditContext
import dev.extframework.boot.archive.ArchiveTreeAuditor
import dev.extframework.boot.archive.DefaultArchiveGraph
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.maven.MavenConstraintNegotiator
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.boot.monad.removeIf
import dev.extframework.common.util.readInputStream
import dev.extframework.common.util.resolve
import dev.extframework.common.util.runCatching
import dev.extframework.extloader.ArchiveGraphView
import dev.extframework.extloader.DefaultExtensionEnvironment
import dev.extframework.extloader.DefaultExtensionLoader
import dev.extframework.extloader.ExtensionResolverView
//import dev.extframework.extloader.RootExtensionEnvironment
import dev.extframework.extloader.extension.DefaultExtensionResolver
import dev.extframework.extloader.extension.ExtensionLayerClassLoader
import dev.extframework.extloader.extension.partition.DefaultPartitionResolver
import dev.extframework.gradle.api.ExtframeworkExtension
import dev.extframework.gradle.api.descriptor
import dev.extframework.`object`.ObjectContainerImpl
import dev.extframework.tooling.api.ExtensionLoader
//import dev.extframework.tooling.api.environment.EnvironmentRegistry
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.environment.ObjectContainerAttribute
import dev.extframework.tooling.api.environment.SetView
import dev.extframework.tooling.api.environment.ValueAttribute
import dev.extframework.tooling.api.environment.dependencyTypesAttrKey
import dev.extframework.tooling.api.environment.wrkDirAttrKey
import dev.extframework.tooling.api.extension.ExtensionClassLoader
import dev.extframework.tooling.api.extension.ExtensionNode
import dev.extframework.tooling.api.extension.ExtensionResolver
import dev.extframework.tooling.api.extension.ExtensionRuntimeModel
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor
import dev.extframework.tooling.api.tweaker.EnvironmentTweaker
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.text.get

internal fun ExtensionLoader(
    path: Path,
    owner: ExtframeworkExtension
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

    return GradleExtensionLoader(resolver, graph, owner)
}

private open class GradleExtensionLoader(
    override val extensionResolver: GradleExtensionResolver,
    graph: ArchiveGraph,
    private val extension: ExtframeworkExtension
) : DefaultExtensionLoader(extensionResolver, graph) {
    protected open val tweaked: MutableSet<ExtensionDescriptor> = HashSet()

    override suspend fun tweak(
        extensions: List<ExtensionNode>,
        environment: ExtensionEnvironment
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
        reference.extension
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
        ExtframeworkPlugin::class.java.getResourceAsStream("/dependencies.txt")?.use {
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