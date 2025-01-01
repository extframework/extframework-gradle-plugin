package dev.extframework.gradle.minecraft

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.resources.asResourceStream
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.archives.zip.ZipFinder
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.launchermeta.handler.Argument
import dev.extframework.launchermeta.handler.Arguments
import dev.extframework.launchermeta.handler.DefaultMetadataProcessor
import dev.extframework.launchermeta.handler.LaunchMetadata
import dev.extframework.launchermeta.handler.LaunchMetadataDownloadType
import dev.extframework.launchermeta.handler.OsType
import dev.extframework.launchermeta.handler.ValueType
import dev.extframework.launchermeta.handler.loadVersionManifest
import dev.extframework.launchermeta.handler.metadata
import dev.extframework.launchermeta.handler.parseMetadata
import kotlinx.coroutines.awaitAll
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.sequences.forEach

data class MinecraftStartMetadata(
    val clientJar: Path,
    val nativesDir: Path,
    val libraries: List<Path>,
    val assets: Path,
    val assetIndex: String,
    val arguments: Arguments,
    val mainClass: String,
)

fun setupMinecraft(
    version: String,
    path: Path,
): AsyncJob<MinecraftStartMetadata> = asyncJob(JobName("Setup Minecraft $version")) {
    val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val manifest = loadVersionManifest().find(version)
        ?: throw IllegalStateException("Failed to find minecraft version: '${version}'. Looked in: 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'.")

    val versionsPath = path resolve "versions"
    val manifestPath = versionsPath resolve "$version.json"
    if (manifestPath.make()) {
        manifest.metadata().merge() copyTo manifestPath
    }

    val metadata = mapper.readValue<LaunchMetadata>(manifestPath.toFile())

    val mcJar = metadata.downloads[LaunchMetadataDownloadType.CLIENT]
        ?.toResource()?.merge()
        ?: throw IllegalArgumentException("Cant find client in launch metadata?")

    val clientPath = versionsPath resolve "$version.jar"
    if (clientPath.make()) {
        mcJar copyTo clientPath
    }

    val metadataProcessor = DefaultMetadataProcessor()

    val libraryPath = path resolve "libraries"

    val extractPath = path resolve "bin"

    val librariesPath = metadata.libraries.mapAsync { lib ->
        val descriptor = SimpleMavenDescriptor.parseDescription(lib.name)!!

        val rawArtifacts = metadataProcessor.deriveArtifacts(OsType.type, lib)
        val artifacts =
            rawArtifacts
                .mapAsync { artifact ->
                    val jarPath = libraryPath resolve (artifact.path
                        ?: "temp${File.separator}${descriptor.name}-${descriptor.version}.jar")

                    if (jarPath.make())
                        artifact.toResource().merge() copyTo jarPath

                    jarPath
                }.awaitAll()

        val extract = lib.extract
        if (extract != null) {
            artifacts.forEach { t ->
                ZipFinder.find(t).use { archive ->
                    archive.reader.entries()
                        .filterNot { entry ->
                            extract.exclude.any { exclude ->
                                entry.name.startsWith(exclude)
                            }
                        }.forEach { entry ->
                            val path = extractPath resolve entry.name

                            if (path.make()) {
                                entry.open().copyTo(path.toFile().outputStream())
                            }
                        }
                }
            }
        }

        rawArtifacts.mapNotNull { it.path }.map {
            libraryPath resolve it
        }
    }.awaitAll().flatten()

    downloadAssets(
        metadata = metadata,
        path resolve "assets" resolve "objects",
        path resolve "assets" resolve "indexes" resolve "${metadata.assetIndex.id}.json",
    )().merge()

    MinecraftStartMetadata(
        clientPath,
        extractPath,
        librariesPath,
        path resolve "assets",
        metadata.assetIndex.id,
        metadata.arguments ?: metadata.minecraftArguments?.let {
            Arguments(
                it.split(" ").map { s -> Argument.Value(ValueType.StringValue(s)) },
                listOf(Argument.Value(ValueType.StringValue("-Djava.library.path=\${natives_directory}")))
            )
        } ?: Arguments(
            listOf(),
            listOf(
                Argument.Value(ValueType.StringValue("-Djava.library.path=\${natives_directory}"))
            )
        ),
        metadata.mainClass,
    )
}