package dev.extframework.gradle

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
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
import dev.extframework.extloader.DefaultExtensionLoader
import dev.extframework.extloader.InternalExtensionEnvironment
import dev.extframework.extloader.extension.DefaultExtensionResolver
import dev.extframework.extloader.extension.partition.DefaultPartitionResolver
import dev.extframework.tooling.api.ExtensionLoader
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor
import java.nio.file.Path
import kotlin.io.path.Path

internal fun ExtensionLoader(
    path: Path,
) : ExtensionLoader {
    val graph = DefaultArchiveGraph(path resolve "archives")
    auditors(graph)
    val dependencyTypes = DependencyTypeContainer(graph)
    dependencyTypes.register("simple-maven", MavenResolverProvider())

    val environment = InternalExtensionEnvironment(
        path,
        graph,
        dependencyTypes,
    )

    environment += GradleExtensionResolver(
        environment,
        path resolve "extensions",
    )

    return DefaultExtensionLoader(environment)
}

private fun auditors(graph: ArchiveGraph) {
    val negotiator = MavenConstraintNegotiator()

    val alreadyLoaded = parsePackagedDependencies().mapTo(HashSet()) {
        negotiator.classify(it)
    }

    val packagedDependencyRemover = object : ArchiveTreeAuditor {
        override fun audit(event: ArchiveTreeAuditContext): Job<ArchiveTreeAuditContext> = job {
            event.copy(tree = event.tree.removeIf {
                alreadyLoaded.contains(negotiator.classify(it.value.descriptor as? SimpleMavenDescriptor ?: return@removeIf false))
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
    environment: ExtensionEnvironment,
    private val path: Path,
) : DefaultExtensionResolver(
    GradleExtensionResolver::class.java.classLoader, environment
) {
    override val partitionResolver: DefaultPartitionResolver = object : DefaultPartitionResolver(
        environment,
        accessBridge
    ) {
        override fun pathForDescriptor(descriptor: PartitionDescriptor, classifier: String, type: String): Path {
            return Path("extensions") resolve super.pathForDescriptor(descriptor, classifier, type)
        }
    }

    override fun pathForDescriptor(descriptor: ExtensionDescriptor, classifier: String, type: String): Path {
        return Path("extensions") resolve super.pathForDescriptor(descriptor, classifier, type)
    }
}