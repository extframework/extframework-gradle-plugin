package dev.extframework.gradle.partition

import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.IArchive
import dev.extframework.boot.archive.TaggedIArchive
import dev.extframework.boot.monad.Either
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.util.mapAsync
import dev.extframework.common.util.runCatching
import dev.extframework.gradle.GradleExceptions
import dev.extframework.gradle.api.GradleEntrypoint
import dev.extframework.gradle.api.GradlePartitionNode
import dev.extframework.tooling.api.exception.StructuredException
import dev.extframework.tooling.api.extension.PartitionRuntimeModel
import dev.extframework.tooling.api.extension.partition.*
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactMetadata
import kotlinx.coroutines.awaitAll
import kotlin.io.path.toPath
import kotlin.reflect.KClass

class GradlePartitionLoader : ExtensionPartitionLoader<GradlePartitionMetadata> {
    override val id: String = "gradle"

    override suspend fun cache(
        metadata: PartitionArtifactMetadata,
        parents: List<Tree<Either<PartitionArtifactMetadata, TaggedIArchive>>>,
        helper: PartitionCacheHelper
    ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> {
        val parentGradlePartitions = helper.erm.parents.mapAsync {
            try {
                helper.cache("gradle", it)
            } catch (_: ArchiveException.ArchiveNotFound) {
                null
            }
        }

        val parentTweakerPartitions = helper.erm.parents.mapAsync {
            try {
                helper.cache("tweaker",  it)
            } catch (_: ArchiveException.ArchiveNotFound) {
                null
            }
        }

        val tweakerPartition = if (helper.erm.partitions.any { model -> model.name == "tweaker" }) {
            listOf(helper.cache("tweaker"))
        } else listOf()

        return helper.newData(
            metadata.descriptor,
            parentGradlePartitions.awaitAll().filterNotNull()
                    + parentTweakerPartitions.awaitAll().filterNotNull()
                    + tweakerPartition
        )
    }

    override fun load(
        metadata: GradlePartitionMetadata,
        reference: ArchiveReference?,
        accessTree: PartitionAccessTree,
        helper: PartitionLoaderHelper
    ): ExtensionPartitionContainer<*, GradlePartitionMetadata> {
        val thisDescriptor by helper::descriptor

        val cl = reference?.let {
            PartitionClassLoader(
                thisDescriptor,
                accessTree,
                it,
                helper.parentClassLoader,
            )
        } ?: throw PartitionLoadException(metadata.name, "This partition must have an associated jar file.") {

        }

        val handle = PartitionArchiveHandle(
            "${helper.erm.name}-gradle",
            cl,
            reference,
            setOf()
        )

        val pluginClassName = metadata.entrypoint

        val pluginClass = runCatching(ClassNotFoundException::class) {
            handle.classloader.loadClass(
                pluginClassName
            )
        } ?: throw StructuredException(
            GradleExceptions.NoEntrypoint,
            description = "Could not init gradle partition because the entrypoint class couldn't be found."
        ) {
            pluginClassName asContext "Gradle plugin class name"
        }

//        val extensionConstructor =
//            runCatching(NoSuchMethodException::class) { extensionClass.getConstructor() }
//                ?: throw Exception("Could not find no-arg constructor in class: '${extensionClassName}' in extension: '${helper.erm.name}'.")
//
//        val instance = extensionConstructor.newInstance() as? GradleEntrypoint
//            ?: throw Exception("Gradle entrypoint class: '$extensionClass' does not extend: '${GradleEntrypoint::class.java.name}'.")
//
        val node = GradlePartitionNode(
            handle,
            accessTree,
            // TODO type check
            pluginClass.kotlin as KClass<out GradleEntrypoint>,
            reference.location.toPath()
        )

        return ExtensionPartitionContainer(
            thisDescriptor,
            metadata,
            node
        )
    }

    override fun parseMetadata(
        partition: PartitionRuntimeModel,
        reference: ArchiveReference?,
        helper: PartitionMetadataHelper
    ): GradlePartitionMetadata {
        if (reference == null) throw PartitionLoadException(
            partition.name,
            "The gradle partition must have a jar."
        )

        val tweakerCls = partition.options["entrypoint"]
            ?: throw IllegalArgumentException("Gradle partition from extension: '${partition.name}' must contain a gradle entrypoint class defined as option: 'entrypoint'.")

        return GradlePartitionMetadata(tweakerCls)
    }

}