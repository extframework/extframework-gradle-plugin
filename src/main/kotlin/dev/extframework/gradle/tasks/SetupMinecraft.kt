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
): Pair<Path, List<Path>> {
    val (mcPath, mcJarPath, libMarkerPath) = mcPaths(version, basePath, mapperType)

    if (libMarkerPath.exists()) {
        return mcJarPath to parseDependencyMarker(libMarkerPath)
    }

    val libsPath = mcPath resolve "libs"
    val metadata = cacheMinecraft(
        version,
        mcJarPath,
        libsPath,
    )

    val markerContent = libsPath.joinToString(separator = "\n") { it.absolutePathString() }
    libMarkerPath.make()
    libMarkerPath.writeBytes(markerContent.toByteArray())

    val mappings = deobfuscator.provider.forIdentifier(version)

    remapJar(
        metadata.mcPath,
        mappings,
        metadata.dependencies,
        deobfuscator.obfuscatedNamespace,
        deobfuscator.deobfuscatedNamespace
    )

    return metadata.mcPath to metadata.dependencies
}

data class McMetadata(
    val mcPath: Path,
    val dependencies: List<Path>,
)

private fun mcPaths(
    version: String,
    basePath: Path,
    mappingsType: String
): Triple<Path, Path, Path> {
    val minecraftPath = basePath resolve "net" resolve "minecraft" resolve "client" resolve version resolve mappingsType

    val dependenciesMarker = minecraftPath resolve ".MINECRAFT_LIBS_MARKER"

    val minecraftJarPath = minecraftPath resolve "minecraft-${version}.jar"

    return Triple(minecraftPath, minecraftJarPath, dependenciesMarker)
}


private fun cacheMinecraft(
    version: String,
    mcJarPath: Path,
    mcLibsPath: Path
): McMetadata = launch {
    val versionManifest = loadVersionManifest()

    val metadata = parseMetadata(
        (versionManifest.find(version)
            ?: throw IllegalArgumentException("Unknown minecraft version: '$version'")).metadata().merge()
    ).merge()

    if (mcJarPath.make()) {
        val clientResource = metadata.downloads[LaunchMetadataDownloadType.CLIENT]?.toResource()?.merge()
            ?: throw IllegalArgumentException("Cant find client in launch metadata?")
        clientResource copyTo mcJarPath
    }

    val processor = DefaultMetadataProcessor()
    val dependencies = processor.deriveDependencies(OsType.type, metadata)
    val paths = dependencies.map {
        val split = it.name.split(':')
        val dClassifier = split.getOrNull(3)
        val (dGroup, dArtifact, dVersion) = split
        val filePath = Path.of(
            dGroup.replace(
                '.',
                File.separatorChar
            )
        ) resolve dArtifact resolve dVersion resolve "${dArtifact}-${dVersion}${if (dClassifier == null) "" else "-${dClassifier}"}.jar"

        val resolvedPath = mcLibsPath resolve filePath

        it.downloads.artifact.toResource().merge() copyTo resolvedPath

        resolvedPath
    }

    McMetadata(mcJarPath, paths)
}

private fun parseDependencyMarker(path: Path): List<Path> {
    return BufferedReader(FileReader(path.toFile())).use {
        it.lineSequence().map(Path::of).toList()

    }
}

fun remapJar(
    jarPath: Path,
    mappings: ArchiveMapping,
    dependencies: List<Path>,
    fromNS: String,
    toNS: String
) {
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




