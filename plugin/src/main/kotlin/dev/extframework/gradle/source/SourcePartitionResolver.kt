package dev.extframework.gradle.source

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.ResourceNotFoundException
import dev.extframework.boot.archive.*
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.common.util.filterDuplicates
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
import dev.extframework.common.util.resolve

open class PartitionSourceArtifactRepository(
    final override val settings: ExtensionRepositorySettings,
    private val prmProvider: (PartitionDescriptor, ExtensionRepositorySettings) -> PartitionRuntimeModel?,
    override val factory: RepositoryFactory<ExtensionRepositorySettings, PartitionSourceArtifactRepository>,
) : ArtifactRepository<ExtensionRepositorySettings, PartitionArtifactRequest, PartitionArtifactMetadata> {
    override val name: String = "partition-sources@${settings.layout.name}"
    private val layout by settings::layout

    override fun get(
        request: PartitionArtifactRequest
    ): AsyncJob<PartitionArtifactMetadata> =
        asyncJob(JobName("Load extension source metadata for: '${request.descriptor}'")) {
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

            PartitionArtifactMetadata(
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
    private val factory = object : RepositoryFactory<ExtensionRepositorySettings, PartitionSourceArtifactRepository> {
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
    override val context: ResolutionContext<ExtensionRepositorySettings, PartitionArtifactRequest, PartitionArtifactMetadata> =
        factory.createContext()

    override fun pathForDescriptor(descriptor: PartitionDescriptor, classifier: String, type: String): Path {
        return Path("extensions") resolve super.pathForDescriptor(descriptor, classifier, type)
    }

    override fun load(
        data: ArchiveData<PartitionDescriptor, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): Job<ExtensionPartitionContainer<*, *>> {
        throw UnsupportedOperationException()
    }

    private fun unknownEnvironment(
        env: String
    ): Nothing = throw StructuredException(
        InternalExceptions.UnknownEnvironmentException,
        message = "Unknown environment $env"
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

    override fun cache(
        artifact: Artifact<PartitionArtifactMetadata>,
        helper: CacheHelper<PartitionDescriptor>
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        val descriptor = artifact.metadata.descriptor
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

        helper.withResource("partition-sources.jar", artifact.metadata.resource)

        val dependencies = cacheSourceDependencies(
            prm,
            descriptor.extension.artifact,
            dependencyTypes.container,
            environment[SourceDependencyTypeContainer],
            helper,
        )().merge()

        loader.cache(
            artifact,
            DefaultPartitionCacheHelper(
                erm, prm, helper, descriptor, dependencies
            )
        )().merge()
    }

    private inner class DefaultPartitionCacheHelper(
        override val erm: ExtensionRuntimeModel,
        override val prm: PartitionRuntimeModel,
        private val helper: CacheHelper<PartitionDescriptor>,
        private val descriptor: PartitionDescriptor,
        private val dependencies: List<Deferred<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>?>>
    ) : PartitionCacheHelper {
        override val defaultEnvironment: String = this@SourcePartitionResolver.defaultEnvironment

        override fun cache(
            reference: String,
            environment: String
        ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> {
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

        override fun cache(
            partition: String,
            environment: String,
            parent: ExtensionParent
        ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
            cache(
                PartitionArtifactRequest(
                    PartitionDescriptor(
                        parent.toDescriptor(),
                        partition,
                        environment
                    )
                ),
                bridge.repositoryFor(parent.toDescriptor()),
                this@SourcePartitionResolver
            )().merge()
        }

        // Delegation
        override val trace: ArchiveTrace by helper::trace

        override fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings> cache(
            request: T,
            repository: R,
            resolver: ArchiveNodeResolver<D, T, *, R, *>
        ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> {
            return helper.cache(request, repository, resolver)
        }

        override fun <D : ArtifactMetadata.Descriptor, M : ArtifactMetadata<D, *>> cache(
            artifact: Artifact<M>,
            resolver: ArchiveNodeResolver<D, *, *, *, M>
        ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> {
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

