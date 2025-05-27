package dev.extframework.gradle.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty

class SetPropertySerializer : JsonSerializer<SetProperty<*>>() {
    override fun serialize(value: SetProperty<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value.isPresent) {
            gen.writeObject(value.get())
        } else {
            gen.writeStartArray()
            gen.writeEndArray()
        }
    }
}

class ListPropertySerializer : JsonSerializer<ListProperty<*>>() {
    override fun serialize(value: ListProperty<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value.isPresent) {
            gen.writeObject(value.get())
        } else {
            gen.writeStartArray()
            gen.writeEndArray()
        }
    }
}

class MapPropertySerializer : JsonSerializer<MapProperty<*, *>>() {
    override fun serialize(value: MapProperty<*, *>, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        val map = value.get()

        map.forEach {
            gen.writeObjectField(it.key.toString(), it.value)
        }
        gen.writeEndObject()
    }
}

class ProviderSerializer : JsonSerializer<Provider<*>>() {
    override fun serialize(value: Provider<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value.isPresent) {
            gen.writeObject(value.get())
        } else {
            gen.writeNull()
        }
    }
}

