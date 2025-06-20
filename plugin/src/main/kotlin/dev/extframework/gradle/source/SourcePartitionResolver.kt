package dev.extframework.gradle.source

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.ResourceNotFoundException
import dev.extframework.boot.archive.*
import dev.extframework.boot.monad.Either
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.common.util.filterDuplicates
import dev.extframework.common.util.resolve
import dev.extframework.gradle.api.source.SourceDependencyTypeContainer
import dev.extframework.tooling.api.environment.EnvironmentRegistry
import dev.extframework.tooling.api.environment.dependencyTypesAttrKey
import dev.extframework.tooling.api.environment.partitionLoadersAttrKey
import dev.extframework.tooling.api.exception.InternalExceptions
import dev.extframework.tooling.api.exception.StructuredException
import dev.extframework.tooling.api.extension.*
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.partition.*
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactMetadata
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.Path

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

// TODO This is mostly copied from ext-loader, poor design, make DefaultPartitionResolver more extensible.
class SourcePartitionResolver(
    private val bridge: ExtensionResolver.AccessBridge,
    private val environmentRegistry: EnvironmentRegistry,
    private val defaultEnvironment: String
) : PartitionResolver {
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

    override val name: String
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


    private fun unknownEnvironment(
        env: String
    ): Nothing = throw StructuredException(
        InternalExceptions.UnknownEnvironmentException,
        description = "Unknown environment $env"
    ) {
        solution("Please registry this environment with the EnvironmentRegistry.")
        environmentRegistry.objects().keys asContext "Registered environments"
    }

    private fun getLoader(
        prm: PartitionRuntimeModel,
        env: String
    ): ExtensionPartitionLoader<ExtensionPartitionMetadata> {
        val environment = environmentRegistry.get(env) ?: unknownEnvironment(env)
        val partitionLoaders = environment[partitionLoadersAttrKey]?.container

        return (partitionLoaders?.get(prm.type) as? ExtensionPartitionLoader<ExtensionPartitionMetadata>)
            ?: throw IllegalArgumentException(
                "Illegal partition type: '${prm.type}', only accepted ones are: '${
                    partitionLoaders?.objects()?.map(
                        Map.Entry<String, ExtensionPartitionLoader<*>>::key
                    ) ?: listOf()
                }'"
            )
    }

    override suspend fun cache(
        metadata: PartitionArtifactMetadata,
        parents: List<Tree<Either<PartitionArtifactMetadata, TaggedIArchive>>>,
        helper: CacheHelper<PartitionDescriptor>
    ): Tree<TaggedIArchive> {
        val descriptor = metadata.descriptor
        val erm = bridge.ermFor(descriptor.extension)
        val prm = erm.namedPartitions[descriptor.partition]
            ?: throw PartitionLoadException(
                descriptor.partition,
                "Unknown partition: '${descriptor.partition}'. It was not defined by this extensions runtime model."
            ) {
                erm.descriptor asContext "Extension name"
            }
        val loader = getLoader(prm, descriptor.environment)
        val environment = environmentRegistry.get(descriptor.environment)
            ?: unknownEnvironment(descriptor.environment)
        val dependencyTypes = environment[dependencyTypesAttrKey]

        helper.withResource("partition-sources.jar", metadata.resource)

        val dependencies = cacheSourceDependencies(
            prm,
            descriptor.extension.artifact,
            dependencyTypes.container,
            environment[SourceDependencyTypeContainer],
            helper,
        )

        return loader.cache(
            metadata,
            parents,
            DefaultPartitionCacheHelper(
                erm, prm, helper, descriptor, dependencies
            )
        )
    }

    private inner class DefaultPartitionCacheHelper(
        override val erm: ExtensionRuntimeModel,
        override val prm: PartitionRuntimeModel,
        private val helper: CacheHelper<PartitionDescriptor>,
        private val descriptor: PartitionDescriptor,
        private val dependencies: List<Deferred<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>?>>
    ) : PartitionCacheHelper {
        override val defaultEnvironment: String = this@SourcePartitionResolver.defaultEnvironment

        override suspend fun cache(
            reference: String,
            environment: String
        ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> {
            return cache(
                PartitionArtifactRequest(
                    PartitionDescriptor(
                        descriptor.extension,
                        reference,
                        environment
                    )
                ),
                bridge.repositoryFor(descriptor.extension),
                this@SourcePartitionResolver,
            )
        }

        override suspend fun cache(
            partition: String,
            environment: String,
            parent: ExtensionParent
        ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> {
            return cache(
                PartitionArtifactRequest(
                    PartitionDescriptor(
                        parent.toDescriptor(),
                        partition,
                        environment
                    )
                ),
                bridge.repositoryFor(parent.toDescriptor()),
                this@SourcePartitionResolver
            )
        }

        // Delegation
        override val trace: ArchiveTrace by helper::trace


        override suspend fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings> cache(
            request: T,
            repository: R,
            resolver: ArchiveNodeResolver<D, T, *, R, *>
        ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> {
            return helper.cache(request, repository, resolver)
        }

        override suspend fun <D : ArtifactMetadata.Descriptor, M : ArtifactMetadata<D, *>> cache(
            artifact: Tree<Either<M, TaggedIArchive>>,
            resolver: ArchiveNodeResolver<D, *, *, *, M>
        ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> {
            return helper.cache(artifact, resolver)
        }

        override fun newData(
            descriptor: PartitionDescriptor,
            parents: List<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>
        ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> {
            return runBlocking { // Decide if it is needed to await for dependencies here or if the await should be moved to the initializer.
                val fullParents = (parents + dependencies.awaitAll().filterNotNull()).filterDuplicates()

                helper.newData(descriptor, fullParents)
            }
        }

        override fun withResource(name: String, resource: Resource) {
            return helper.withResource(name, resource)
        }
    }
}

