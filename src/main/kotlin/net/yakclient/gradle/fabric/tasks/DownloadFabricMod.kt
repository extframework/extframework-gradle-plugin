package net.yakclient.gradle.fabric.tasks

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.createContext
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.jobs.launch
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.ResourceAlgorithm
import net.yakclient.archives.Archives
import net.yakclient.common.util.copyTo
import net.yakclient.common.util.resolve
import net.yakclient.gradle.*
import net.yakclient.gradle.fabric.FabricMappingProvider.Companion.INTERMEDIARY_NAMESPACE
import net.yakclient.gradle.tasks.RemapTask
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.TaskAction
import java.nio.file.Path
import java.util.*

abstract class DownloadFabricMod : DefaultTask() {
    private val basePath = project.projectDir.toPath() resolve "build-ext" resolve "fabric"

    @get:Input
    abstract val configuration: Property<String>

    @get:Internal
    val output: Provider<Map<String, ConfigurableFileTree>> = project.provider {
        project.extensions.getByType(YakClientExtension::class.java).partitions.associate {
            it.name to project.fileTree(basePath resolve it.name).builtBy(this@DownloadFabricMod)
        }
    }

    @TaskAction
    fun download() {
        val yakclient = project.extensions.getByType(YakClientExtension::class.java)
        val dependencies = project.configurations.getByName(configuration.get()).dependencies

        dependencies.forEach { dependency ->
            project.repositories.firstNotNullOfOrNull {
                val request = SimpleMavenArtifactRequest(
                    SimpleMavenDescriptor(
                        dependency.group!!,
                        dependency.name,
                        dependency.version!!,
                        null
                    )
                )

                val baseArtifact = launch {
                    val contexts = project.repositories.map {
                        when (it) {
                            is DefaultMavenLocalArtifactRepository -> SimpleMavenRepositorySettings.local()
                            is MavenArtifactRepository -> SimpleMavenRepositorySettings.default(
                                it.url.toString(),
                                preferredHash = ResourceAlgorithm.SHA1,
                                requireResourceVerification = false
                            )

                            else -> throw IllegalArgumentException("Repository type: '${it.name}' is not currently supported.")
                        }
                    }.map { SimpleMaven.createContext(it) }

                    val artifact = contexts.firstNotNullOfOrNull {
                        it.getAndResolve(request)().getOrNull()
                    }

                    artifact ?: throw IllegalArgumentException("Unable to find fabric mod: '$dependency'")
                }

                fun setupModResource(path: Path, name: String, resource: Resource) {
                    val jarPath = path resolve name
                    resource copyTo jarPath

                    Archives.find(jarPath, Archives.Finders.ZIP_FINDER).use { archive ->
                        archive.reader.entries()
                            .filter { it.name.endsWith(".jar") }
                            .forEach {
                                setupModResource(path resolve "files", it.name.substringAfterLast('/'), it.resource)
                            }
                    }
                }

                fun setupMod(artifact: Artifact<SimpleMavenArtifactMetadata>) {
                    val descriptor = artifact.metadata.descriptor

                    yakclient.partitions.forEach { partition ->
                        val artifactPath =
                            basePath resolve partition.name resolve descriptor.artifact resolve descriptor.version

                        val resource = (artifact.metadata.resource
                            ?: throw Exception("Fabric mod: '$descriptor' does not have a jar associated with it (there is no mod present here.)"))

                        setupModResource(artifactPath, "${descriptor.artifact}-${descriptor.version}.jar", resource)
                    }


                    artifact.children.forEach {
                        setupMod(it)
                    }
                }

                setupMod(baseArtifact)
            }
        }
    }
}

fun registerFabricModTask(
    yakclient: YakClientExtension,
    project: Project,
    configuration: Configuration
): Provider<DownloadFabricMod> {
    val downloadModTask = project.tasks.register("downloadFabricMods", DownloadFabricMod::class.java) {
        it.configuration.set(configuration.name)
        it.outputs.upToDateWhen { false }
        it.finalizedBy(project.tasks.withType(RemapTask::class.java))
    }

    project.afterEvaluate {
        yakclient.partitions
            .filterIsInstance<VersionedPartitionHandler>()
            .map { partition ->
                project.tasks.register(
                    "remap${
                        partition.name.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(
                                Locale.getDefault()
                            ) else it.toString()
                        }
                    }FabricMods", RemapTask::class.java
                ) {
                    it.dependsOn(downloadModTask)
                    it.inputOutputFiles.setFrom(downloadModTask.get().output.map { output ->
                        output[partition.name]!!
                    })
                    it.mappingIdentifier.set(partition.supportedVersions.first()) // TODO not a perfect solution
                    it.sourceNamespace.set(INTERMEDIARY_NAMESPACE)
                    it.targetNamespace.set(partition.mappings.deobfuscatedNamespace)
                }
            }
    }



    return downloadModTask
}