package com.kaolinmc.kiln.source

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.kaolinmc.boot.archive.ArchiveException
import com.kaolinmc.boot.archive.ArchiveNodeResolver
import com.kaolinmc.boot.archive.CacheHelper
import com.kaolinmc.boot.archive.IArchive
import com.kaolinmc.boot.dependency.DependencyResolverProvider
import com.kaolinmc.boot.dependency.DependencyTypeContainer
import com.kaolinmc.boot.monad.Tagged
import com.kaolinmc.boot.monad.Tree
import com.kaolinmc.boot.util.mapAsync
import com.kaolinmc.kiln.api.source.DependencySourceProvider
import com.kaolinmc.`object`.ObjectContainer
import com.kaolinmc.tooling.api.extension.PartitionRuntimeModel
import com.kaolinmc.tooling.api.extension.partition.PartitionLoadException
import kotlinx.coroutines.Deferred

internal suspend fun cacheSourceDependencies(
    partition: PartitionRuntimeModel,
    extName: String,
    dependencyProviders: DependencyTypeContainer,
    sources: ObjectContainer<DependencySourceProvider<*>>,
    helper: CacheHelper<*>,
): List<Deferred<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>?>> =
    partition.dependencies.mapAsync { dependency ->
        if (partition.repositories.isEmpty()) {
            throw PartitionLoadException(
                partition.name, "Partition: '${partition.name}' has no defined repositories but has dependencies!"
            ) {
                extName asContext "Extension name"

                solution("Define at least 1 repository in this partition.")
            }
        }

        val requests = partition.repositories.mapNotNull { settings ->
            val provider: DependencyResolverProvider<*, *, *> =
                dependencyProviders.get(settings.type) ?: throw PartitionLoadException(
                    partition.name, "Failed to find dependency type: '${settings.type}'"
                ) {
                    partition.name asContext "Partition name"
                }

            val depReq: ArtifactRequest<*> = provider.parseRequest(dependency) ?: return@mapNotNull null

            val repoSettings = provider.parseSettings(settings.settings) ?: throw PartitionLoadException(
                partition.name, "Invalid repository settings."
            ) {
                extName asContext "Extension name"
                settings.settings asContext "Repository settings"
                provider.id asContext "Dependency resolution provider"
            }

            Triple(depReq, repoSettings, provider)
        }

        if (requests.isEmpty()) {
            throw PartitionLoadException(
                partition.name,
                "Failed to parse dependency request.",
            ) {
                extName asContext "Extension name"
                dependency asContext "Raw dependency request"
                partition.repositories asContext "Attempted repositories"
            }
        }

        val cacheResult = requests.mapNotNull cache@{ (request, settings, provider) ->
            val sources = (sources[provider.id] ?: return@cache null)
                    as DependencySourceProvider<ArtifactRequest<ArtifactMetadata.Descriptor>>

            runCatching {
                helper.cache(
                    sources.tagSource(request as ArtifactRequest<ArtifactMetadata.Descriptor>),
                    settings,
                    sources.resolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, ArtifactRequest<ArtifactMetadata.Descriptor>, *, RepositorySettings, *>
                )
            }
        }

        if (cacheResult.isEmpty() || cacheResult.all { it.exceptionOrNull() is ArchiveException.ArchiveNotFound }) {
            return@mapAsync null
        }

        val successfulJob = cacheResult.find { cacheResult ->
            cacheResult.isSuccess
        } ?: throw PartitionLoadException(
            partition.name,
            "An unrecoverable error occurred when caching dependencies",
            cacheResult
                .mapNotNull { it.exceptionOrNull() }
                .first { it !is ArchiveException.ArchiveNotFound }
        ) {
            dependency asContext "Raw dependency request" // We want the raw dependency request because there was an issue with every single dependency provider (and we correctly assume that all may have different dependency descriptor types)
            requests.map { it.second } asContext "Attempted repositories"
            extName asContext "Extension name"
        }

        successfulJob.getOrThrow()
    }
