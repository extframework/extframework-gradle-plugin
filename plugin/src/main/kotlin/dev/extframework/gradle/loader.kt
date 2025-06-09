package dev.extframework.gradle

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.job
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.archive.ArchiveTreeAuditContext
import dev.extframework.boot.archive.ArchiveTreeAuditor
import dev.extframework.boot.archive.DefaultArchiveGraph
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.maven.MavenConstraintNegotiator
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.boot.monad.removeIf
import dev.extframework.common.util.filterDuplicates
import dev.extframework.common.util.readInputStream
import dev.extframework.common.util.resolve
import dev.extframework.common.util.runCatching
import dev.extframework.extloader.DefaultExtensionLoader
import dev.extframework.extloader.RootExtensionEnvironment
import dev.extframework.extloader.extension.DefaultExtensionResolver
import dev.extframework.extloader.extension.partition.DefaultPartitionResolver
import dev.extframework.extloader.extension.partition.TweakerPartitionNode
import dev.extframework.extloader.handleStructuredException
import dev.extframework.gradle.api.ExtframeworkExtension
import dev.extframework.`object`.ObjectContainerImpl
import dev.extframework.tooling.api.ExtensionLoader
import dev.extframework.tooling.api.environment.EnvironmentRegistry
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.extension.ExtensionNode
import dev.extframework.tooling.api.extension.ExtensionResolver
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.partition.ExtensionPartitionContainer
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor
import dev.extframework.tooling.api.tweaker.EnvironmentTweaker
import dev.extframework.tooling.api.uber.UberArtifactRequest
import dev.extframework.tooling.api.uber.UberDescriptor
import dev.extframework.tooling.api.uber.UberParentRequest
import dev.extframework.tooling.api.uber.UberRepositorySettings
import dev.extframework.tooling.api.uber.UberResolver
import java.nio.file.Path
import kotlin.io.path.Path

internal fun ExtensionLoader(
    path: Path,
    owner: ExtframeworkExtension
): ExtensionLoader {
    val graph = ClassesArchiveGraph(path resolve "archives")
    auditors(graph, owner)
    val dependencyTypes = DependencyTypeContainer(graph)
    dependencyTypes.register("simple-maven", MavenResolverProvider())

    val environment = RootExtensionEnvironment(
        "root",
        path,
        dependencyTypes,
    )

    val environmentRegistry: EnvironmentRegistry = ObjectContainerImpl()
    environmentRegistry.register("root", environment)

    val resolver = GradleExtensionResolver(
        owner.project.buildscript.classLoader,
        environmentRegistry,
        "root"
    )

    return GradleExtensionLoader(resolver, graph, environment, environmentRegistry, owner)
}

private class GradleExtensionLoader(
    extensionResolver: ExtensionResolver,
    graph: ArchiveGraph,
    rootEnvironment: ExtensionEnvironment,
    environmentRegistry: EnvironmentRegistry,
    private val extension: ExtframeworkExtension
) : DefaultExtensionLoader(extensionResolver, graph, rootEnvironment, environmentRegistry) {
    override fun tweak(
        extensions: List<ExtensionNode>,
        environment: ExtensionEnvironment
    ): AsyncJob<Unit> = asyncJob {
        runCatching {
            // This is copied from the TweakerPartitionLoader which is just messy, there should be a better way to do this.
            val tweakers = extension.build.tweakers.map {
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
                it.tweak(environment)().merge()
            }
        }.handleStructuredException()
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
private fun auditors(graph: ArchiveGraph, extension: ExtframeworkExtension) {
//    val buildScriptArtifactRemover = object : ArchiveTreeAuditor {
//        override fun audit(event: ArchiveTreeAuditContext): Job<ArchiveTreeAuditContext> = job {
//            event.copy(tree = event.tree.removeIf {
//                extension.build.content.contains(it.value.descriptor.name)
//            }!!)
//        }
//    }
//
//    graph.auditors = graph.auditors.chain(buildScriptArtifactRemover)

    val negotiator = MavenConstraintNegotiator()

    val alreadyLoaded = parsePackagedDependencies().mapTo(HashSet()) {
        negotiator.classify(it)
    }

    val packagedDependencyRemover = object : ArchiveTreeAuditor {
        override fun audit(event: ArchiveTreeAuditContext): Job<ArchiveTreeAuditContext> = job {
            event.copy(tree = event.tree.removeIf {
                alreadyLoaded.contains(
                    negotiator.classify(
                        it.value.descriptor as? SimpleMavenDescriptor ?: return@removeIf false
                    )
                )
            }!!)
        }
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

internal class GradleExtensionResolver(
    classloader: ClassLoader,
    environmentRegistry: EnvironmentRegistry,
    defaultEnvironment: String,
) : DefaultExtensionResolver(
    classloader, environmentRegistry, defaultEnvironment
) {
    override val partitionResolver: DefaultPartitionResolver = object : DefaultPartitionResolver(
        accessBridge, environmentRegistry, defaultEnvironment
    ) {
        override fun pathForDescriptor(descriptor: PartitionDescriptor, classifier: String, type: String): Path {
            return Path("extensions") resolve super.pathForDescriptor(descriptor, classifier, type)
        }
    }

    override fun pathForDescriptor(descriptor: ExtensionDescriptor, classifier: String, type: String): Path {
        return Path("extensions") resolve super.pathForDescriptor(descriptor, classifier, type)
    }
}