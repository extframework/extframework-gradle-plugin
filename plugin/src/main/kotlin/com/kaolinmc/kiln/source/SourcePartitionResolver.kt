package com.kaolinmc.kiln.source

import com.durganmcbroom.artifact.resolver.ArtifactRepository
import com.durganmcbroom.artifact.resolver.MetadataRequestException
import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.resources.ResourceNotFoundException
import com.kaolinmc.boot.archive.*
import com.kaolinmc.boot.monad.Either
import com.kaolinmc.boot.monad.Tree
import com.kaolinmc.common.util.resolve
import com.kaolinmc.extloader.extension.partition.DefaultPartitionResolver
import com.kaolinmc.kiln.api.source.sourceDependencyTypesAttrKey
import com.kaolinmc.tooling.api.ExtensionLoader
import com.kaolinmc.tooling.api.environment.ExtensionEnvironment
import com.kaolinmc.tooling.api.environment.dependencyTypesAttrKey
import com.kaolinmc.tooling.api.extension.PartitionRuntimeModel
import com.kaolinmc.tooling.api.extension.artifact.ExtensionRepositorySettings
import com.kaolinmc.tooling.api.extension.descriptor
import com.kaolinmc.tooling.api.extension.partition.ExtensionPartitionContainer
import com.kaolinmc.tooling.api.extension.partition.PartitionLoadException
import com.kaolinmc.tooling.api.extension.partition.artifact.PartitionArtifactMetadata
import com.kaolinmc.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import com.kaolinmc.tooling.api.extension.partition.artifact.PartitionDescriptor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.nio.file.Path
import kotlin.io.path.Path

// TODO This is mostly copied from ext-loader, poor design, make DefaultPartitionResolver more extensible.
class SourcePartitionResolver(
    private val environment: ExtensionEnvironment
) : DefaultPartitionResolver(
    environment[ExtensionLoader].extensionResolver.accessBridge,
    environment
) {
    private val bridge
        get() = environment[ExtensionLoader].extensionResolver.accessBridge

    override val factory = object : RepositoryFactory<ExtensionRepositorySettings, PartitionSourceArtifactRepository> {
        override fun createNew(settings: ExtensionRepositorySettings): PartitionSourceArtifactRepository {
            return PartitionSourceArtifactRepository(
                settings,
                { p, settings ->
                    bridge.ermFor(p.extension).partitions.find {
                        it.name == p.partition
                    }?.takeIf { bridge.repositoryFor(p.extension) == settings }
                },
                this
            )
        }
    }

    override val id: String
        get() = "extension-partition:sources"

    override fun pathForDescriptor(descriptor: PartitionDescriptor, classifier: String, type: String): Path {
        return Path("extensions") resolve super.pathForDescriptor(descriptor, classifier, type)
    }

    override fun load(
        data: ArchiveData<PartitionDescriptor, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): ExtensionPartitionContainer<*, *> {
        throw UnsupportedOperationException()
    }

    override suspend fun cache(
        metadata: PartitionArtifactMetadata,
        parents: List<Tree<Either<PartitionArtifactMetadata, TaggedIArchive>>>,
        helper: CacheHelper<PartitionDescriptor>
    ): Tree<TaggedIArchive> = coroutineScope {
        val descriptor = metadata.descriptor
        val erm = bridge.ermFor(descriptor.extension)
        val prm = erm.namedPartitions[descriptor.partition]
            ?: throw PartitionLoadException(
                descriptor.partition,
                "Unknown partition: '${descriptor.partition}'. It was not defined by this extensions runtime model."
            ) {
                erm.descriptor asContext "Extension name"
            }
        val loader = getLoader(prm)
        val dependencyTypes = environment[dependencyTypesAttrKey]

        helper.withResource("partition-sources.jar", metadata.resource)

        val dependencies = async {
            cacheSourceDependencies(
                prm,
                descriptor.extension.artifact,
                dependencyTypes.container,
                environment[sourceDependencyTypesAttrKey].container,
                helper,
            ).awaitAll().filterNotNull()
        }

        loader.cache(
            metadata,
            parents,
            DefaultPartitionCacheHelper(
                erm, prm, helper, descriptor, dependencies
            )
        )
    }

    open class PartitionSourceArtifactRepository(
        final override val settings: ExtensionRepositorySettings,
        private val prmProvider: (PartitionDescriptor, ExtensionRepositorySettings) -> PartitionRuntimeModel?,
        override val factory: RepositoryFactory<ExtensionRepositorySettings, PartitionSourceArtifactRepository>,
    ) : ArtifactRepository<ExtensionRepositorySettings, PartitionArtifactRequest, PartitionArtifactMetadata> {
        override val name: String = "partition-sources@${settings.layout.name}"
        private val layout by settings::layout

        override suspend fun get(
            request: PartitionArtifactRequest
        ): PartitionArtifactMetadata {
            val (extensionDescriptor, partition) = request.descriptor
            val (group, artifact, version) = extensionDescriptor

            val prm = prmProvider(request.descriptor, settings)

            if (prm == null) {
                throw MetadataRequestException.MetadataNotFound(request.descriptor, "prm/erm.json")
            }

            val jar = try {
                layout.resourceOf(
                    group,
                    artifact,
                    version,
                    "$partition-sources",
                    "jar",
                )
            } catch (_: ResourceNotFoundException) {
                null
            } catch (e: Throwable) {
                throw e
            }

            return PartitionArtifactMetadata(
                request.descriptor,
                jar,
            )
        }
    }
}

