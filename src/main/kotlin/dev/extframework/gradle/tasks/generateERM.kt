//package dev.extframework.gradle.tasks
//
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.extframework.common.util.make
import dev.extframework.gradle.ExtFrameworkExtension
import dev.extframework.gradle.util.ListPropertySerializer
import dev.extframework.gradle.util.MapPropertySerializer
import dev.extframework.gradle.util.PropertySerializer
import dev.extframework.gradle.util.SetPropertySerializer
import dev.extframework.internal.api.extension.ExtensionRepository
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

// TODO we want this or not?
//abstract class GenerateErm : DefaultTask() {
//    private val extframework
//        get() = project.extensions.getByName("extension") as ExtFrameworkExtension
//
//    @get:OutputFile
//    val ermPath: File =
//        (project.layout.buildDirectory.asFile.get().toPath() resolve "libs" resolve "erm.json").toFile()
//
//    @TaskAction
//    fun generateErm() {
//        val mapper = ObjectMapper()
//            .registerModule(KotlinModule.Builder().build())
//            .registerModule(
//                SimpleModule()
//                    .addSerializer(Property::class.java, PropertySerializer())
//                    .addSerializer(SetProperty::class.java, SetPropertySerializer())
//                    .addSerializer(MapProperty::class.java, MapPropertySerializer())
//                    .addSerializer(ListProperty::class.java, ListPropertySerializer())
//            )
//
//        extframework.eagerModel {
//            it.repositories.addAll(
//                project.repositories.map {
//                    when (it) {
//                        is DefaultMavenLocalArtifactRepository -> mutableMapOf(
//                            "location" to it.url.path,
//                            "type" to "local"
//                        )
//
//                        is DefaultMavenArtifactRepository -> mutableMapOf(
//                            "location" to it.url.toString(),
//                            "type" to "default"
//                        )
//
//                        else -> throw Exception("Unknown repository type: ${it::class}")
//                    }
//                }
//            )
//        }
//        val ermAsBytes =
//            mapper.writeValueAsBytes(extframework.erm.get())
//
//        ermPath.toPath().make()
//        ermPath.writeBytes(ermAsBytes)
//    }
//}
//
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
                    .addSerializer(Property::class.java, PropertySerializer())
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

                        is DefaultMavenLocalArtifactRepository -> mutableMapOf(
                            "location" to it.url.path,
                            "type" to "local"
                        )

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
//
//
//
//internal fun Project.registerGenerateErmTask() = project.tasks.register("generateErm", GenerateErm::class.java)
