package net.yakclient.gradle.tasks

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.launch
import com.durganmcbroom.resources.ResourceAlgorithm
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.boot.maven.MavenResolverProvider
import net.yakclient.common.util.copyTo
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.api.extension.ExtensionPartition
import net.yakclient.components.extloader.extension.artifact.ExtensionArtifactMetadata
import net.yakclient.components.extloader.extension.artifact.ExtensionRepositoryFactory
import net.yakclient.gradle.YakClientExtension
import net.yakclient.gradle.write
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.net.URI
import java.nio.file.Files


private fun newEmptyArchive() = object : ArchiveReference {
    private val entries: MutableMap<String, () -> ArchiveReference.Entry> = HashMap()
    override val isClosed: Boolean = false
    override val location: URI
        get() = TODO("Not yet implemented")
    override val modified: Boolean = entries.isNotEmpty()
    override val name: String? = null
    override val reader: ArchiveReference.Reader = object : ArchiveReference.Reader {
        override fun entries(): Sequence<ArchiveReference.Entry> {
            return entries.values.asSequence().map { it() }
        }

        override fun of(name: String): ArchiveReference.Entry? {
            return entries[name]?.invoke()
        }
    }
    override val writer = object : ArchiveReference.Writer {
        override fun put(entry: ArchiveReference.Entry) {
            entries[entry.name] = {entry}
        }

        fun put(name: String, entry: () -> ArchiveReference.Entry) {
            entries[name] = entry
        }

        override fun remove(name: String) {
            entries.remove(name)
        }

    }

    override fun close() {}
}

abstract class DownloadExtensions : DefaultTask() {
    private val basePath = project.projectDir.toPath() resolve "build-ext"

    @get:Input
    abstract val configuration: Property<String>

    @get:Internal
    val output: ConfigurableFileTree = project.fileTree(basePath).builtBy(this)

    @TaskAction
    fun download() {
        val yakclient = project.extensions.getByType(YakClientExtension::class.java)
        project.configurations.getByName(configuration.get()).dependencies.forEach { dependency ->
            val dependencyType = DependencyTypeContainer(
                ArchiveGraph(Files.createTempDirectory("yak-gradle-m2"))
            )
            dependencyType.register("simple-maven", MavenResolverProvider())

            val factory = ExtensionRepositoryFactory(dependencyType)

            val request = SimpleMavenArtifactRequest(
                SimpleMavenDescriptor(
                    dependency.group!!,
                    dependency.name,
                    dependency.version!!,
                    null
                )
            )

            val baseArtifact = launch {
                val artifact = project.repositories.map {
                    when (it) {
                        is DefaultMavenLocalArtifactRepository -> SimpleMavenRepositorySettings.local()
                        is MavenArtifactRepository -> SimpleMavenRepositorySettings.default(
                            it.url.toString(),
                            preferredHash = ResourceAlgorithm.SHA1
                        )

                        else -> throw IllegalArgumentException("Repository type: '${it.name}' is not currently supported.")
                    }
                }.map(factory::createNew).map {
                    ResolutionContext(
                        it,
                        it.stubResolver,
                        factory.artifactComposer
                    )
                }.firstNotNullOfOrNull {
                    it.getAndResolve(request)().getOrNull()
                }
                artifact ?: throw IllegalArgumentException("Unable to find extension: '$dependency'")
            }

            fun downloadArtifact(artifact: Artifact<*>): List<ArchiveReference> {
                val erm = (artifact.metadata as ExtensionArtifactMetadata).erm

                val jarPrefix = "${erm.name}-${erm.version}"

                val resourcePath = Files.createTempFile(jarPrefix, ".jar")

                val baseResource = artifact.metadata.resource ?: return listOf()

                resourcePath.make()
                baseResource copyTo resourcePath

                val archive = Archives.find(resourcePath, Archives.Finders.ZIP_FINDER)

                val subArchives: Map<ExtensionPartition, ArchiveReference> =
                    erm.partitions
                        .filter { it.type == "main" }
                        .associateWith { newEmptyArchive() }


                archive.reader.entries().mapNotNull { e ->
                    val splitPath = e.name.split("/")
                    val partition = subArchives
                        .filter { (it) -> e.name.startsWith(it.path) }
                        .maxByOrNull { (it) ->
                            it.path.split("/").zip(splitPath)
                                .takeWhile { (f, s) -> f == s }
                                .count()
                        } ?: return@mapNotNull null

                    e.name to partition
                }.forEach { (name, partition) ->
                    val newName = name.removePrefix(partition.key.path).removePrefix("/")
                    val e = archive.reader[name]!!

                    partition.value.writer.put(
                        ArchiveReference.Entry(
                            newName,
                            e.resource,
                            e.isDirectory,
                            archive
                        )
                    )
                }

                val children = artifact.children.map {
                    it
                }.flatMap {
                    downloadArtifact(it)
                }

                subArchives.map { (partition, archive) ->
                    basePath resolve
                            erm.groupId.replace('.', '/') resolve
                            erm.name resolve
                            erm.version resolve
                            "$jarPrefix-${partition.name}.jar" to archive
                }.forEach { (partPath, u) ->
                    partPath.make()
                    u.write(partPath)
                }

                return subArchives.values + children
            }

            downloadArtifact(baseArtifact).forEach {
                it.close()
            }
        }
    }
}