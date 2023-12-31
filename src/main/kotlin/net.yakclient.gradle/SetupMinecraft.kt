package net.yakclient.gradle

import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.transform.transformArchive
import net.yakclient.archives.Archives
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.launchermeta.handler.*
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.writeBytes

fun getMinecraftPaths(version: String, basePath: Path) : List<Path>? {
    val minecraftPath = basePath resolve "net" resolve "minecraft" resolve "client" resolve version
    val minecraftJarPath = minecraftPath resolve "minecraft-${version}.jar"

    val dependenciesMarker = minecraftPath resolve ".MINECRAFT_LIBS_MARKER"

    if (!Files.exists(minecraftJarPath)) return null
    if (!Files.exists(dependenciesMarker)) return null

    return parseDependencyMarker(dependenciesMarker) + minecraftJarPath
}

fun setupMinecraft(
    version: String,
    basePath: Path,
    deobfuscator: MinecraftDeobfuscator,
    mapperType: String // Redundant but we need to make sure...
) : Pair<Path, List<Path>> {
    val (metadata, cached) = cacheMinecraft(
        version,
        basePath,
        mapperType
    )
    val (mc, dependencies) = metadata
    val mappings = deobfuscator.provider.forIdentifier(version)

    if (cached) remapJar(mc, mappings, dependencies, deobfuscator.obfuscatedNamespace, deobfuscator.deobfuscatedNamespace)

    return mc to dependencies
}

data class McMetadata(
    val mcPath: Path,
    val dependencies: List<Path>,
)

private fun cacheMinecraft(version: String, basePath: Path, mappingsType: String): Pair<McMetadata, Boolean> {
    val minecraftPath = basePath resolve "net" resolve "minecraft" resolve "client" resolve version resolve mappingsType
    val minecraftJarPath = minecraftPath resolve "minecraft-${version}.jar"
//    val mappingsPath = minecraftPath resolve "minecraft-mappings-${version}.txt"

    val libPath = minecraftPath resolve "libs"

    val dependenciesMarker = minecraftPath resolve ".MINECRAFT_LIBS_MARKER"

    val b = !(minecraftJarPath.exists()  && dependenciesMarker.exists())
    if (b) {
        val versionManifest = loadVersionManifest()

        val metadata = parseMetadata(
            (versionManifest.find(version)
                ?: throw IllegalArgumentException("Unknown minecraft version: '$version'")).metadata()
        )

        // Download minecraft jar
        if (minecraftJarPath.make()) {

            val clientResource = metadata.downloads[LaunchMetadataDownloadType.CLIENT]?.toResource()
                ?: throw IllegalArgumentException("Cant find client in launch metadata?")
            clientResource copyToBlocking minecraftJarPath
        }

//        // Download mappings
//        if (mappingsPath.make()) {
//            val mappingsResource = metadata.downloads[LaunchMetadataDownloadType.CLIENT_MAPPINGS]?.toResource()
//                ?: throw IllegalArgumentException("Cant find client mappings in launch metadata?")
//            mappingsResource copyToBlocking mappingsPath
//        }

        if (dependenciesMarker.make()) {
            val processor = DefaultMetadataProcessor()
            val dependencies = processor.deriveDependencies(OsType.type, metadata)
            val paths = dependencies.map {
                val split = it.name.split(':')
                val dClassifier = split.getOrNull(3)
                val (dGroup, dArtifact, dVersion) = split
                val toPath = libPath resolve (dGroup.replace(
                    '.',
                    File.separatorChar
                )) resolve dArtifact resolve dVersion resolve "${dArtifact}-${dVersion}${if (dClassifier == null) "" else "-${dClassifier}"}.jar"

                it.downloads.artifact.toResource() copyToBlocking toPath

                toPath
            }

            val markerContent = paths.joinToString(separator = "\n") { it.absolutePathString() }

            dependenciesMarker.writeBytes(markerContent.toByteArray())
        }
    }

    return McMetadata(minecraftJarPath, parseDependencyMarker(dependenciesMarker)) to b
}

private fun parseDependencyMarker(path: Path) : List<Path> {
    val reader = BufferedReader(FileReader(path.toFile()))

    return reader.lineSequence().map(Path::of).toList()
}

fun remapJar(jarPath: Path, mappings: ArchiveMapping, dependencies: List<Path>, fromNS: String, toNS: String) {
    val archive = Archives.find(jarPath, Archives.Finders.ZIP_FINDER)
    val libArchives = dependencies.map(Archives.Finders.ZIP_FINDER::find)

    transformArchive(
        archive,
        libArchives,
        mappings,
        fromNS,
        toNS,
    )

    val jar = Files.createTempFile(jarPath.name, ".jar")

    JarOutputStream(FileOutputStream(jar.toFile())).use { target ->
        archive.reader.entries().forEach { e ->
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

    Files.copy(jar, jarPath, StandardCopyOption.REPLACE_EXISTING)
}




