package dev.extframework.gradle.fabric.tasks

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.ArtifactMetadata.Descriptor
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.launch
import com.durganmcbroom.jobs.mapException
import com.durganmcbroom.resources.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.archives.Archives
import dev.extframework.common.util.Hex
import dev.extframework.common.util.resolve
import dev.extframework.gradle.fabric.FabricMappingProvider.Companion.INTERMEDIARY_NAMESPACE
import dev.extframework.gradle.tasks.RemapTask
import dev.extframework.gradle.write
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Path
import java.util.*


// TODO this is copied from fabric-ext, make a common project that houses this.
data class ModrinthProjectVersion(
    val gameVersions: List<String>,
    val loaders: List<String>,
    val id: String,
    val projectId: String,
    val authorId: String,
    val featured: Boolean,
    val name: String,
    val versionNumber: String,
    val changelog: String?,
    val changelogUrl: String?,
    val datePublished: String,
    val downloads: Int,
    val versionType: String,
    val status: String,
    val requestedStatus: String?,
    val files: List<ModrinthProjectVersionFile>,
    val dependencies: List<ModrinthProjectVersionDependency>
)

data class ModrinthProjectVersionFile(
    val hashes: ModrinthProjectVersionHashes,
    val url: String,
    val filename: String,
    val primary: Boolean,
    val size: Int,
    val fileType: String?
)

data class ModrinthProjectVersionHashes(
    val sha1: String,
    val sha512: String
)

data class ModrinthProjectVersionDependency(
    val versionId: String?,
    val projectId: String,
    val fileName: String?,
    val dependencyType: String
)

data class ModrinthProjectVersionListing(
    val id: String
)

object ModrinthRepositorySettings : RepositorySettings

data class ModrinthModDescriptor(
    val projectId: String,
    val versionId: String,
) : Descriptor {
    override val name: String = "$projectId:$versionId"
}

data class ModrinthModArtifactRequest(
    override val descriptor: ModrinthModDescriptor,
) : ArtifactRequest<ModrinthModDescriptor>

typealias ModrinthModParentInfo = ArtifactMetadata.ParentInfo<ModrinthModArtifactRequest, ModrinthRepositorySettings>

class ModrinthModArtifactMetadata(
    descriptor: ModrinthModDescriptor,
    val resource: Resource,
    parents: List<ModrinthModParentInfo>
) : ArtifactMetadata<ModrinthModDescriptor, ModrinthModParentInfo>(
    descriptor,
    parents
)

const val MODRINTH_VERSION_ENDPOINT = "https://api.modrinth.com/v2/version/"

class ModrinthArtifactRepository :
    ArtifactRepository<ModrinthRepositorySettings, ModrinthModArtifactRequest, ModrinthModArtifactMetadata> {
    override val factory: RepositoryFactory<ModrinthRepositorySettings, ArtifactRepository<ModrinthRepositorySettings, ModrinthModArtifactRequest, ModrinthModArtifactMetadata>>
        get() = Modrinth
    override val name: String = "modrinth"
    override val settings: ModrinthRepositorySettings = ModrinthRepositorySettings
    private val mapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .addModule(KotlinModule.Builder().build())
        .build()

    private fun encoded(str: String): String {
        return URLEncoder.encode(str, "UTF-8")
    }

    override fun get(request: ModrinthModArtifactRequest): AsyncJob<ModrinthModArtifactMetadata> = asyncJob() {
        val version = mapper.readValue<ModrinthProjectVersion>(runCatching {
            URI.create(
                MODRINTH_VERSION_ENDPOINT + request.descriptor.versionId
            ).toURL().toResource().open().toByteArray()
        }.mapException {
            if (it is ResourceNotFoundException) {
                MetadataRequestException.MetadataNotFound(
                    request.descriptor,
                    MODRINTH_VERSION_ENDPOINT + request.descriptor.versionId
                )
            } else it
        }.merge())

        val primaryFile = version.files.find {
            it.primary
        } ?: version.files.firstOrNull() ?: throw MetadataRequestException.MetadataNotFound(
            request.descriptor,
            "primary modrinth file"
        )

        val rawResource = URI.create(primaryFile.url).toURL().toResource()

        val resource = VerifiedResource(
            rawResource,
            ResourceAlgorithm.SHA1,
            Hex.parseHex(primaryFile.hashes.sha1)
        )

        val parents = version.dependencies
            .filter { it.dependencyType == "required" }
            .mapNotNull {
                val versionId = it.versionId ?: return@mapNotNull null
                ModrinthModDescriptor(
                    it.projectId,
                    versionId
                )
            }
            .map(::ModrinthModArtifactRequest).map { ModrinthModParentInfo(it, listOf(ModrinthRepositorySettings)) }

        ModrinthModArtifactMetadata(
            request.descriptor,
            resource,
            parents
        )
    }

}

object Modrinth : RepositoryFactory<ModrinthRepositorySettings, ModrinthArtifactRepository> {
    override fun createNew(settings: ModrinthRepositorySettings): ModrinthArtifactRepository {
        return ModrinthArtifactRepository()
    }
}


abstract class DownloadFabricMod : DefaultTask() {
    private val basePath = project.projectDir.resolve("build-ext").resolve("fabric-unmapped").toPath()

    @get:Input
    abstract val mods: ListProperty<String>

    @get:OutputFiles
    val output: ConfigurableFileTree = project.fileTree(
        basePath
    ).builtBy(this)


    @TaskAction
    fun download() {
        // TODO Hacky
        if (project.gradle.startParameter.isOffline) {
            logger.warn("Attempted to download fabric mods but Gradle is in offline mode, ignoring for now...")
            return
        }

        mods.get().forEach { mod ->
            val repoContext = Modrinth.createContext()
            val (projectId, versionId) = mod.split(":")
            val request = ModrinthModArtifactRequest(
                ModrinthModDescriptor(projectId, versionId),
            )

            val baseArtifact = launch {
                repoContext.getAndResolve(request, ModrinthRepositorySettings)().mapException {
                    Exception(
                        "Unable to find fabric mod: '$mod'",
                        it
                    )
                }.merge()
            }

            fun setupModResource(path: Path, name: String, resource: InputStream) {
                val jarPath = path resolve name
                resource.copyTo(jarPath.toFile().outputStream())

                Archives.find(jarPath, Archives.Finders.ZIP_FINDER).use { archive ->
                    archive.reader.entries()
                        .filter { it.name.endsWith(".jar") }
                        .forEach {
                            setupModResource(path resolve "files", it.name.substringAfterLast('/'), it.open())
                        }

                    archive.writer.remove("META-INF/MANIFEST.MF")

                    archive.write(jarPath)
                }
            }

            suspend fun setupMod(artifact: Artifact<ModrinthModArtifactMetadata>) {
                val descriptor = artifact.metadata.descriptor

                val artifactPath = basePath resolve descriptor.projectId resolve descriptor.versionId

                val resource = artifact.metadata.resource

                setupModResource(artifactPath, "${descriptor.projectId}-${descriptor.versionId}.jar",
                    ByteArrayInputStream(resource.open().toByteArray()))

                artifact.parents.forEach {
                    setupMod(it)
                }
            }

            runBlocking {
                setupMod(baseArtifact)
            }
        }
    }
}

fun registerFabricModTask(
    project: Project,
    partition: String,
    mappingTarget: String,
    version: String,
    output: Path
): Task {
    val downloadModTask = project.tasks.maybeCreate("downloadFabricMods", DownloadFabricMod::class.java)

    val it = project.tasks.maybeCreate(
        "remap${
            partition.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                    Locale.getDefault()
                ) else it.toString()
            }
        }FabricMods", RemapTask::class.java
    )


    it.dependsOn(downloadModTask)

    it.input.setFrom(downloadModTask.output)
    it.mappingIdentifier.set(version)
    it.sourceNamespace.set(INTERMEDIARY_NAMESPACE)
    it.targetNamespace.set(mappingTarget)
    it.output.set(output.toFile())

    return it
}