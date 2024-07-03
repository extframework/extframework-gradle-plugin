package dev.extframework.gradle.tasks

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.gradle.ExtFrameworkExtension
import org.gradle.api.*
import org.gradle.api.provider.*
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
        val ermAsBytes =
            mapper.writeValueAsBytes(extframework.erm.get())

        ermPath .toPath().make()
        ermPath.writeBytes(ermAsBytes)
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
