package dev.extframework.gradle.tasks

import com.durganmcbroom.artifact.resolver.simple.maven.layout.mavenLocal
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.extframework.common.util.filterDuplicates
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.gradle.ExtframeworkPlugin.Companion.EXTFRAMEWORK_CENTRAL
import dev.extframework.gradle.api.ExtensionConfig
import dev.extframework.gradle.api.ExtframeworkExtension
import dev.extframework.gradle.api.MutableExtensionRuntimeModel
import dev.extframework.tooling.api.extension.ExtensionParent
import dev.extframework.tooling.api.extension.ExtensionRepository
import dev.extframework.tooling.api.extension.ExtensionRuntimeModel
import dev.extframework.tooling.api.extension.PartitionRuntimeModel
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Paths
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

// TODO we want this or not?
abstract class GenerateErm : DefaultTask() {
    private val extframework
        get() = project.extensions.getByName("extension") as ExtframeworkExtension

    @get:OutputFile
    val ermPath: File =
        (project.layout.buildDirectory.asFile.get().toPath() resolve "libs" resolve "erm.json").toFile()

    @TaskAction
    fun generateErm() {
        val model = setupModel(
            extframework
        )

        val ermAsBytes = writeErm(model)

        ermPath.toPath().make()
        ermPath.writeBytes(ermAsBytes)
    }

    companion object {
        fun writeErm(model: ExtensionRuntimeModel): ByteArray {
            val mapper = ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
                .addMixIn(ExtensionRuntimeModel::class.java, ERMJacksonMixin::class.java)

            val bytes = mapper.writeValueAsBytes(model)

            return bytes
        }

        abstract class ERMJacksonMixin {
            @get:JsonIgnore
            abstract val namedPartitions: Map<String, PartitionRuntimeModel>
        }


        fun setupModel(
            extension: ExtframeworkExtension,
        ): ExtensionRuntimeModel {
            return setupModel(extension.configuration, extension.project, extension.model)
        }

        fun setupModel(
            configuration: ExtensionConfig,
            project: Project,
            model: MutableExtensionRuntimeModel
        ): ExtensionRuntimeModel {
            val model = model.toImmutable()

            val partitionRepositories = project.repositories.mapNotNull {
                ExtensionRepository(
                    "simple-maven",
                    serialize(it) ?: return@mapNotNull null
                )
            }.filterDuplicates()

            val extensionRepositories = ArrayList<Map<String, String>>()
            val parents = ArrayList<ExtensionParent>()
            val configuredRepositories = configuration.repositories
            for ((name, attr) in configuration.parents) {
                val repository = when (attr.repository) {
                    "local" -> mutableMapOf(
                        "location" to mavenLocal,
                        "type" to "local"
                    )

                    "central" -> mutableMapOf(
                        "location" to EXTFRAMEWORK_CENTRAL,
                        "type" to "default"
                    )

                    else -> mutableMapOf(
                        "location" to configuredRepositories.custom[attr.repository]!!,
                        "type" to "default"
                    )
                }
                extensionRepositories.add(repository)

                val parent = if (attr.isProjectBuild) {
                    val parentProject = project.project(name)
                    val extension = parentProject.extensions.getByType(ExtframeworkExtension::class.java)
                    ExtensionParent(
                        extension.model.groupId.get(),
                        extension.model.name.get(),
                        extension.model.version.get(),
                    )
                } else {
                    ExtensionParent(
                        attr.group!!,
                        name,
                        attr.version!!
                    )
                }

                parents.add(parent)
            }
            // TODO want this?
            if (configuredRepositories.local) {
                extensionRepositories.add(
                    mutableMapOf(
                        "location" to mavenLocal,
                        "type" to "local"
                    )
                )
            }
            if (configuredRepositories.central) {
                extensionRepositories.add(
                    mutableMapOf(
                        "location" to EXTFRAMEWORK_CENTRAL,
                        "type" to "default"
                    )
                )
            }
            for (entry in configuredRepositories.custom) {
                extensionRepositories.add(
                    mutableMapOf(
                        "location" to configuredRepositories.custom[entry.value]!!,
                        "type" to "default"
                    )
                )
            }

            return model.copy(
                repositories = (extensionRepositories + model.repositories).filterDuplicates(),
                parents = (parents + model.parents).toSet(),
                partitions = model.partitions.mapTo(HashSet()) {
                    it.copy(
                        repositories = it.repositories + partitionRepositories
                    )
                }
            )
        }

        private fun serialize(repo: ArtifactRepository): MutableMap<String, String>? = when (repo) {
            is DefaultMavenLocalArtifactRepository -> mutableMapOf(
                "location" to Paths.get(repo.url).toString(),
                "type" to "local"
            )

            is DefaultMavenArtifactRepository -> mutableMapOf(
                "location" to repo.url.toString(),
                "type" to "default"
            )

            else -> null
        }
    }
}
