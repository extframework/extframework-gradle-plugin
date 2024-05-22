package net.yakclient.gradle.fabric.tasks

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.createContext
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMaven
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.launch
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.ResourceAlgorithm
import net.yakclient.archives.Archives
import net.yakclient.common.util.copyTo
import net.yakclient.common.util.resolve
import net.yakclient.gradle.MutableExtensionPartition
import net.yakclient.gradle.fabric.FabricMappingProvider.Companion.INTERMEDIARY_NAMESPACE
import net.yakclient.gradle.tasks.RemapTask
import net.yakclient.gradle.write
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import java.nio.file.Path
import java.util.*

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
            project.repositories.firstNotNullOfOrNull {
                val request = SimpleMavenArtifactRequest(
                    mod
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

                    artifact ?: throw IllegalArgumentException("Unable to find fabric mod: '$mod'")
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

                        archive.writer.remove("META-INF/MANIFEST.MF")

                        archive.write(jarPath)
                    }
                }

                fun setupMod(artifact: Artifact<SimpleMavenArtifactMetadata>) {
                    val descriptor = artifact.metadata.descriptor

                    val artifactPath = basePath resolve descriptor.artifact resolve descriptor.version

                    val resource = (artifact.metadata.resource
                        ?: throw Exception("Fabric mod: '$descriptor' does not have a jar associated with it (there is no mod present here.)"))

                    setupModResource(artifactPath, "${descriptor.artifact}-${descriptor.version}.jar", resource)

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
    project: Project,
    partition: MutableExtensionPartition,
    mappingTarget: String,
    version: String,
    output: Path
): TaskProvider<*> {
    val downloadModTask = project.tasks.maybeCreate("downloadFabricMods", DownloadFabricMod::class.java)

    return project.tasks.register(
        "remap${
            partition.name.get().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                    Locale.getDefault()
                ) else it.toString()
            }
        }FabricMods", RemapTask::class.java
    ) {
        it.dependsOn(downloadModTask)

        it.input.setFrom(downloadModTask.output)
        it.mappingIdentifier.set(version)
        it.sourceNamespace.set(INTERMEDIARY_NAMESPACE)
        it.targetNamespace.set(mappingTarget)
        it.output.set(output.toFile())
    }
}