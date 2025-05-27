package dev.extframework.gradle.partition

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.job
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.IArchive
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
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
    override val type: String = "gradle"
    override fun cache(
        artifact: Artifact<PartitionArtifactMetadata>,
        helper: PartitionCacheHelper
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        val parentGradlePartitions = helper.erm.parents.mapAsync {
            val result = helper.cache("gradle", helper.defaultEnvironment, it)()

            val ex = result.exceptionOrNull()
            if (ex != null) {
                if (ex is ArchiveException.ArchiveNotFound) null
                else throw ex
            }

            result.getOrNull()
        }

        val parentTweakerPartitions = helper.erm.parents.mapAsync {
            val result = helper.cache("tweaker", helper.defaultEnvironment, it)()

            val ex = result.exceptionOrNull()
            if (ex != null) {
                if (ex is ArchiveException.ArchiveNotFound) null
                else throw ex
            }

            result.getOrNull()
        }

        val tweakerPartition = if (helper.erm.partitions.any { model -> model.name == "tweaker" }) {
            listOf(helper.cache("tweaker", helper.defaultEnvironment)().merge())
        } else listOf()

        helper.newData(
            artifact.metadata.descriptor,
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
    ): Job<ExtensionPartitionContainer<*, GradlePartitionMetadata>> = job {
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
            message = "Could not init gradle partition because the entrypoint class couldn't be found."
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

        ExtensionPartitionContainer(
            thisDescriptor,
            metadata,
            node
        )
    }

    override fun parseMetadata(
        partition: PartitionRuntimeModel,
        reference: ArchiveReference?,
        helper: PartitionMetadataHelper
    ): Job<GradlePartitionMetadata> = job() {
        if (reference == null) throw PartitionLoadException(
            partition.name,
            "The gradle partition must have a jar."
        )

        val tweakerCls = partition.options["entrypoint"]
            ?: throw IllegalArgumentException("Gradle partition from extension: '${partition.name}' must contain a gradle entrypoint class defined as option: 'entrypoint'.")

        GradlePartitionMetadata(tweakerCls)
    }

}