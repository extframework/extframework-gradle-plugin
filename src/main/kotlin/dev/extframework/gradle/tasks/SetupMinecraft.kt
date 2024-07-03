package dev.extframework.gradle.tasks

import com.durganmcbroom.jobs.launch
import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.transform.transformArchive
import dev.extframework.archives.Archives
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.gradle.deobf.MinecraftDeobfuscator
import dev.extframework.gradle.write
import dev.extframework.launchermeta.handler.*
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

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

private fun cacheMinecraft(version: String, basePath: Path, mappingsType: String): Pair<McMetadata, Boolean> = launch {
    val minecraftPath = basePath resolve "net" resolve "minecraft" resolve "client" resolve version resolve mappingsType
    val minecraftJarPath = minecraftPath resolve "minecraft-${version}.jar"

    val libPath = minecraftPath resolve "libs"

    val dependenciesMarker = minecraftPath resolve ".MINECRAFT_LIBS_MARKER"

    val b = !(minecraftJarPath.exists()  && dependenciesMarker.exists())
    if (b) {
        val versionManifest = loadVersionManifest()

        val metadata = parseMetadata(
            (versionManifest.find(version)
                ?: throw IllegalArgumentException("Unknown minecraft version: '$version'")).metadata().merge()
        ).merge()

        // Download minecraft jar
        if (minecraftJarPath.make()) {

            val clientResource = metadata.downloads[LaunchMetadataDownloadType.CLIENT]?.toResource()?.merge()
                ?: throw IllegalArgumentException("Cant find client in launch metadata?")
            clientResource copyTo minecraftJarPath
        }

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

                it.downloads.artifact.toResource().merge() copyTo toPath

                toPath
            }

            val markerContent = paths.joinToString(separator = "\n") { it.absolutePathString() }

            dependenciesMarker.writeBytes(markerContent.toByteArray())
        }
    }

    McMetadata(minecraftJarPath, parseDependencyMarker(dependenciesMarker)) to b
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

    archive.write(jarPath)
}




