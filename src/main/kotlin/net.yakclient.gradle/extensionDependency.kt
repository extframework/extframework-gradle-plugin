package net.yakclient.gradle

import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.common.util.resource.SafeResource
import net.yakclient.components.extloader.api.extension.ExtensionPartition
import net.yakclient.components.extloader.api.extension.ExtensionRuntimeModel
import net.yakclient.launchermeta.handler.copyToBlocking
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream


private fun newEmptyArchive(): ArchiveReference {
    return object : ArchiveReference {
        private val entries: MutableMap<String, ArchiveReference.Entry> = HashMap()


        override val isClosed: Boolean = false
        override val location: URI
            get() = TODO("Not yet implemented")
        override val modified: Boolean = entries.isNotEmpty()
        override val name: String? = null
        override val reader: ArchiveReference.Reader = object : ArchiveReference.Reader {
            override fun entries(): Sequence<ArchiveReference.Entry> {
                return entries.values.asSequence()
            }

            override fun of(name: String): ArchiveReference.Entry? {
                return entries[name]
            }
        }
        override val writer: ArchiveReference.Writer = object : ArchiveReference.Writer {
            override fun put(entry: ArchiveReference.Entry) {
                entries[entry.name] = entry
            }

            override fun remove(name: String) {
                entries.remove(name)
            }

        }

        override fun close() {}
    }
}

private fun ArchiveReference.write(path: Path) {
    JarOutputStream(FileOutputStream(path.toFile())).use { target ->
        reader.entries().forEach { e ->
            val entry = JarEntry(e.name)

            target.putNextEntry(entry)

            val eIn = e.resource.open()

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

// A List of Pairs is preferable as it only connotes iteration and not look up by keys, which is
// not something an ExtensionPartition is required to implement.
internal fun downloadBuildExtension(
    path: Path,
    resource: SafeResource,
    erm: ExtensionRuntimeModel
): List<Pair<ExtensionPartition, Path>> {
    val jarPrefix = "${erm.name}-${erm.version}"

    val basePath = path resolve erm.groupId.replace('.', '/') resolve erm.name resolve erm.version
    val resourcePath = basePath resolve "$jarPrefix.jar"

    resourcePath.make()
    resource copyToBlocking resourcePath

    val archive = Archives.find(resourcePath, Archives.Finders.ZIP_FINDER)

    val subArchives = (erm.versionPartitions + erm.mainPartition + listOfNotNull(erm.tweakerPartition)).associate {
        it to newEmptyArchive()
    }

    archive.reader.entries().forEach { e ->
        val splitPath = e.name.split("/")
        val partition = subArchives
            .filter { (it) -> e.name.startsWith(it.path) }
            .maxByOrNull { (it) ->
                it.path.split("/").zip(splitPath)
                    .takeWhile { (f, s) -> f == s }
                    .count()
            } ?: return@forEach

        partition.value.writer.put(
            ArchiveReference.Entry(
                e.name.removePrefix(partition.key.path).removePrefix("/"),
                e.resource,
                e.isDirectory,
                partition.value
            )
        )
    }

    return subArchives.map { (t, u) ->
        val partPath = basePath resolve "partitions" resolve "$jarPrefix-${t.name}.jar"
        partPath.make()
        u.write(partPath)
        t to partPath
    }
}