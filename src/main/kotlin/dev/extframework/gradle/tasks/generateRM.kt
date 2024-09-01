package dev.extframework.gradle.tasks

import com.durganmcbroom.artifact.resolver.simple.maven.layout.mavenLocal
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.gradle.ExtFrameworkExtension
import dev.extframework.internal.api.extension.ExtensionRepository
import org.gradle.api.*
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.provider.*
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class GenerateErm : DefaultTask() {
    private val extframework
        get() = project.extensions.getByName("extension") as ExtFrameworkExtension

    @get:OutputFile
    val ermPath: File =
        (project.layout.buildDirectory.asFile.get().toPath() resolve "libs" resolve "erm.json").toFile()

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

        extframework.eagerModel {
            it.repositories.addAll(
                project.repositories.map {
                    when (it) {
                        is DefaultMavenLocalArtifactRepository -> mutableMapOf(
                            "location" to it.url.path,
                            "type" to "local"
                        )

                        is DefaultMavenArtifactRepository -> mutableMapOf(
                            "location" to it.url.toString(),
                            "type" to "default"
                        )

                        else -> throw Exception("Unknown repository type: ${it::class}")
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

private class SetPropertySerializer : JsonSerializer<SetProperty<*>>() {
    override fun serialize(value: SetProperty<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value.isPresent) {
            gen.writeObject(value.get())
        } else {
            gen.writeStartArray()
            gen.writeEndArray()
        }
    }
}

private class ListPropertySerializer : JsonSerializer<ListProperty<*>>() {
    override fun serialize(value: ListProperty<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value.isPresent) {
            gen.writeObject(value.get())
        } else {
            gen.writeStartArray()
            gen.writeEndArray()
        }
    }
}

private class MapPropertySerializer : JsonSerializer<MapProperty<*, *>>() {
    override fun serialize(value: MapProperty<*, *>, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        val map = value.get()

        map.forEach {
            gen.writeObjectField(it.key.toString(), it.value)
        }
        gen.writeEndObject()
    }
}

private class PropertySerializer : JsonSerializer<Property<*>>() {
    override fun serialize(value: Property<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value.isPresent) {
            gen.writeObject(value.get())
        } else {
            gen.writeNull()
        }
    }
}

internal fun Project.registerGenerateErmTask() = project.tasks.register("generateErm", GenerateErm::class.java)
