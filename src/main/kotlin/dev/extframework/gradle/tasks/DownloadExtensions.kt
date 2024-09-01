package dev.extframework.gradle.tasks

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ArtifactException
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.launch
import com.durganmcbroom.resources.ResourceAlgorithm
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.resolve
import dev.extframework.extloader.extension.artifact.ExtensionRepositoryFactory
import dev.extframework.extloader.extension.partition.artifact.PartitionRepositoryFactory
import dev.extframework.gradle.descriptor
import dev.extframework.internal.api.extension.artifact.ExtensionArtifactMetadata
import dev.extframework.internal.api.extension.artifact.ExtensionArtifactRequest
import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
import dev.extframework.internal.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.internal.api.extension.partition.artifact.PartitionDescriptor
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

abstract class DownloadExtensions : DefaultTask() {
    private val basePath = project.projectDir.toPath() resolve "build-ext" resolve "extensions"

    @get:Input
    abstract val dependencies: ListProperty<String>

    @get:OutputFiles
    val output: ConfigurableFileTree = project.fileTree(basePath).builtBy(this)

    @TaskAction
    fun download() {
        dependencies.get().forEach { dependency ->
            val dependencyType = DependencyTypeContainer(
                ArchiveGraph.from(Files.createTempDirectory("extframework-m2"))
            )
            dependencyType.register("simple-maven", MavenResolverProvider())

            val extensionFactory = ExtensionRepositoryFactory(dependencyType)

            val request = ExtensionArtifactRequest(
                ExtensionDescriptor.parseDescriptor(
                    dependency
                )
            )

            launch {
                val artifact = project.repositories.map {
                    when (it) {
                        is DefaultMavenLocalArtifactRepository -> SimpleMavenRepositorySettings.local()
                        is MavenArtifactRepository -> SimpleMavenRepositorySettings.default(
                            it.url.toString(),
                            preferredHash = ResourceAlgorithm.SHA1
                        )

                        else -> throw IllegalArgumentException("Repository type: '${it.name}' is not currently supported.")
                    }
                }.map(extensionFactory::createNew).map {
                    ResolutionContext(
                        it,
                    )
                }.firstNotNullOfOrNull {
                    val result = it.getAndResolve(request)()
                    result.getOrNull() ?: if (result.exceptionOrNull() !is ArtifactException.ArtifactNotFound)
                        throw result.exceptionOrNull()!!
                    else null
                }

                val baseArtifact = artifact ?: throw IllegalArgumentException("Unable to find extension: '$dependency'")

                val partitionFactory = PartitionRepositoryFactory(extensionFactory)

                fun downloadArtifact(artifact: Artifact<*>) {
                    val extensionArtifactMetadata = artifact.metadata as ExtensionArtifactMetadata
                    val erm = extensionArtifactMetadata.erm

                    val jarPrefix = "${erm.name}-${erm.version}"

                    erm.partitions.forEach { partitionRef ->
                        val req = partitionFactory.createNew(
                            extensionArtifactMetadata.repository
                        ).get(
                            PartitionArtifactRequest(
                                PartitionDescriptor(
                                    erm.descriptor,
                                    partitionRef.name
                                )
                            )
                        )().merge()

                        val path = basePath resolve
                                erm.groupId.replace('.', '/') resolve
                                erm.name resolve
                                erm.version resolve
                                "$jarPrefix-${partitionRef.name}.jar"
                        req.resource copyTo path
                    }

                    artifact.parents.forEach {
                        downloadArtifact(it)
                    }
                }

                downloadArtifact(baseArtifact)
            }
        }
    }
}
