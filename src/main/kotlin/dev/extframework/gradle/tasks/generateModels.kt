//package dev.extframework.gradle.tasks
//
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.gradle.ExtFrameworkExtension
import dev.extframework.gradle.util.ListPropertySerializer
import dev.extframework.gradle.util.MapPropertySerializer
import dev.extframework.gradle.util.ProviderSerializer
import dev.extframework.gradle.util.SetPropertySerializer
import dev.extframework.internal.api.extension.ExtensionRepository
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Path

// TODO we want this or not?
abstract class GenerateErm : DefaultTask() {
    private val extframework
        get() = project.extensions.getByName("extension") as ExtFrameworkExtension

    @get:OutputFile
    val ermPath: File =
        (project.layout.buildDirectory.asFile.get().toPath() resolve  "libs" resolve "erm.json").toFile()

    @TaskAction
    fun generateErm() {
        val mapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(
                SimpleModule()
                    .addSerializer(Provider::class.java, ProviderSerializer())
                    .addSerializer(SetProperty::class.java, SetPropertySerializer())
                    .addSerializer(MapProperty::class.java, MapPropertySerializer())
                    .addSerializer(ListProperty::class.java, ListPropertySerializer())
            )

        extframework.eagerModel {
            it.repositories.addAll(
                project.repositories.map { repo ->
                    when (repo) {
                        is DefaultMavenLocalArtifactRepository -> mutableMapOf(
                            "location" to Path.of(repo.url).toString(),
                            "type" to "local"
                        )

                        is DefaultMavenArtifactRepository -> mutableMapOf(
                            "location" to repo.url.toString(),
                            "type" to "default"
                        )

                        else -> throw Exception("Unknown repository type: ${repo::class}")
                    }
                }
            )
        }
        val ermAsBytes =
            mapper.writeValueAsBytes(extframework.erm.get())

        ermPath.toPath().make()
        ermPath.writeBytes(ermAsBytes)
    }
}

abstract class GeneratePrm : DefaultTask() {
    private val extframework
        get() = project.extensions.getByName("extension") as ExtFrameworkExtension

    @get:Input
    abstract val partitionName: Property<String>

    @get:OutputFile
    val prmPath: RegularFileProperty =
        project.objects.fileProperty()
            .convention(project.layout.buildDirectory.file(
                project.provider { "libs/prm//${partitionName.get()}-prm.json" }
            ))


    @TaskAction
    fun generateErm() {
        val mapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(
                SimpleModule()
                    .addSerializer(Property::class.java, ProviderSerializer())
                    .addSerializer(SetProperty::class.java, SetPropertySerializer())
                    .addSerializer(MapProperty::class.java, MapPropertySerializer())
                    .addSerializer(ListProperty::class.java, ListPropertySerializer())
            )

        val partition = extframework.partitions
            .first { it.partition.name == partitionName.get() }
            .partition

        partition.repositories.addAll(
            project.repositories.map {
                ExtensionRepository(
                    "simple-maven",
                    when (it) {
                        is DefaultMavenArtifactRepository -> mutableMapOf(
                            "location" to it.url.toString(),
                            "type" to "default"
                        )

                        is DefaultMavenLocalArtifactRepository -> {
                            println(it.url)
                            println(Path.of(it.url).toString())
                            mutableMapOf(
                                "location" to Path.of(it.url).toString(),
                                "type" to "local"
                            )
                        }

                        else -> throw Exception("Unknown repository type: ${it::class}")
                    }

                )
            }
        )

        val prmAsBytes = mapper.writeValueAsBytes(
            partition
        )

        val path = prmPath.asFile.get()
        path.toPath().make()
        path.writeBytes(prmAsBytes)
    }
}

internal fun Project.registerGenerateErmTask() = tasks.register("generateErm", GenerateErm::class.java)
