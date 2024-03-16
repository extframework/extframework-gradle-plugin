package net.yakclient.gradle

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.launch
import com.durganmcbroom.resources.ResourceAlgorithm.SHA1
import com.durganmcbroom.resources.openStream
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.boot.maven.MavenResolverProvider
import net.yakclient.common.util.copyTo
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.api.extension.ExtensionTweakerPartition
import net.yakclient.components.extloader.api.extension.ExtensionVersionPartition
import net.yakclient.components.extloader.api.extension.MainVersionPartition
import net.yakclient.components.extloader.extension.artifact.ExtensionArtifactMetadata
import net.yakclient.components.extloader.extension.artifact.ExtensionRepositoryFactory
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream


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
            throw UnsupportedOperationException("")
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


private fun ArchiveReference.write(path: Path) {
    JarOutputStream(FileOutputStream(path.toFile())).use { target ->
        reader.entries().forEach { e ->
            val entry = JarEntry(e.name)

            target.putNextEntry(entry)

            val eIn = e.resource.openStream()

            //Stolen from https://stackoverflow.com/questions/1281229/how-to-use-jaroutputstream-to-create-a-jar-file
            val buffer = ByteArray(1024)

            while (true) {
                val count: Int = eIn.read(buffer)
                if (count == -1) break

                target.write(buffer, 0, count)
            }

            target.closeEntry()
        }
    }
}

abstract class DownloadExtensions : DefaultTask() {
    private val basePath = project.projectDir.toPath() resolve "build-ext"
    private val yakclient
        get() = project.extensions.getByType(YakClientExtension::class.java)

    @get:Input
    abstract val notation: Property<Any>

    @get:Internal
    val output: Provider<Map<String, ConfigurableFileTree>> = project.provider {
        (yakclient.partitions.map { it.name } + listOfNotNull(
            "main",
            if (yakclient.tweakerPartition.isPresent) "tweaker" else null
        )).associateWith {
            project.fileTree(basePath resolve it) { tree ->
                tree.builtBy(this)
            }
        }
    }

    @TaskAction
    fun download() {
        val dependency = project.dependencies.add("extension", notation.get())!!

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
            project.repositories.map {
                when (it) {
                    is DefaultMavenLocalArtifactRepository -> SimpleMavenRepositorySettings.local()
                    is MavenArtifactRepository -> SimpleMavenRepositorySettings.default(
                        it.url.toString(),
                        preferredHash = SHA1
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
            } ?: throw IllegalArgumentException("Unable to find extension: '$notation'")
        }


        fun downloadArtifact(artifact: Artifact<*>) {
            val erm = (artifact.metadata as ExtensionArtifactMetadata).erm

            val jarPrefix = "${erm.name}-${erm.version}"

            val resourcePath = Files.createTempFile(jarPrefix, ".jar")

            val baseResource = artifact.metadata.resource ?: return

            resourcePath.make()
            baseResource copyTo resourcePath

            val archive = Archives.find(resourcePath, Archives.Finders.ZIP_FINDER)

            val subArchives =
                (erm.versionPartitions + erm.mainPartition + listOfNotNull(erm.tweakerPartition)).associateWith { newEmptyArchive() }

            archive.reader.entries().associate { e ->
                val splitPath = e.name.split("/")
                val partition = subArchives
                    .filter { (it) -> e.name.startsWith(it.path) }
                    .maxByOrNull { (it) ->
                        it.path.split("/").zip(splitPath)
                            .takeWhile { (f, s) -> f == s }
                            .count()
                    }!!

                e.name to partition
            }.forEach { (name, partition) ->
                archive.reader
                val newName = name.removePrefix(partition.key.path).removePrefix("/")
                partition.value.writer.put(
                    newName
                ) {
                    val e = archive.reader[name]!!

                    ArchiveReference.Entry(
                        newName,
                        e.resource,
                        e.isDirectory,
                        archive
                    )
                }
            }

            subArchives.flatMap { (partition, archive) ->
                val whichPartition = when (partition) {
                    is MainVersionPartition -> listOf("main")
                    is ExtensionTweakerPartition -> listOf("tweaker")
                    is ExtensionVersionPartition -> yakclient.partitions.filter {
                        it.supportedVersions.get().intersect(partition.supportedVersions).isNotEmpty()
                    }.map { it.name }

                    else -> throw IllegalArgumentException("Unknown partition type: '${partition::class.java.name}'")
                }

                whichPartition.map {
                    basePath resolve
                            it resolve
                            erm.groupId.replace('.', '/') resolve
                            erm.name resolve
                            erm.version resolve
                            "$jarPrefix-${partition.name}.jar" to archive
                }
            }.forEach { (partPath, u) ->
                partPath.make()
                u.write(partPath)
            }

            artifact.children.map {
                it
            }.forEach {
                downloadArtifact(it)
            }
        }

        downloadArtifact(baseArtifact)
    }
}