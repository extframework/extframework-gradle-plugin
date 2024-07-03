package dev.extframework.gradle.fabric

import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.openStream
import com.durganmcbroom.resources.toResource
import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.MappingsProvider
import dev.extframework.archive.mapper.parsers.tiny.TinyV1MappingsParser
import dev.extframework.boot.store.DataAccess
import dev.extframework.boot.store.DataStore
import dev.extframework.boot.store.DelegatingDataStore
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.resolve
import dev.extframework.components.extloader.extension.mapping.MojangExtensionMappingProvider
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists


class FabricMappingProvider(
    private val rawMappings: MappingsProvider
) : MappingsProvider {
    override val namespaces: Set<String> = setOf(MojangExtensionMappingProvider.FAKE_TYPE, INTERMEDIARY_NAMESPACE)

    override fun forIdentifier(identifier: String): ArchiveMapping {
        val parse = rawMappings.forIdentifier(identifier)
        return parse.mapNamespaces(
            "official" to MojangExtensionMappingProvider.FAKE_TYPE,
            "intermediary" to INTERMEDIARY_NAMESPACE
        )
    }

    companion object {
        const val INTERMEDIARY_NAMESPACE = "fabric:intermediary"
    }
}

internal class RawFabricMappingProvider private constructor(
    val store: DataStore<String, Resource>
) : MappingsProvider {

    constructor(path: Path) : this(DelegatingDataStore(IntermediaryMappingAccess(path)))

    override val namespaces: Set<String> = setOf("named", "intermediary")

    override fun forIdentifier(identifier: String): ArchiveMapping {
        val mappingData = store[identifier] ?: run {
            val url = URL("https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/$identifier.tiny")

            val resource = url.toResource()
            store.put(identifier, resource)
            resource
        }

        return TinyV1MappingsParser.parse(mappingData.openStream())
    }
}

private class IntermediaryMappingAccess(
    private val path: Path
) : DataAccess<String, Resource> {
    override fun read(key: String): Resource? {
        val versionPath = path resolve "intermediary-mappings-$key.json"

        if (!versionPath.exists()) return null

        return versionPath.toResource()
    }

    override fun write(key: String, value: Resource) {
        val versionPath = path resolve "intermediary-mappings-$key.json"
        versionPath.deleteIfExists()

        value copyTo versionPath
    }
}