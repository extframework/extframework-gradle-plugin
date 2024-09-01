package dev.extframework.gradle.deobf

import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.openStream
import dev.extframework.archive.mapper.ArchiveMapping
import dev.extframework.archive.mapper.MappingsProvider
import dev.extframework.archive.mapper.parsers.proguard.ProGuardMappingParser
import dev.extframework.boot.store.CachingDataStore
import dev.extframework.boot.store.DataAccess
import dev.extframework.boot.store.DataStore
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.resolve
import dev.extframework.common.util.toResource
import dev.extframework.launchermeta.handler.clientMappings
import dev.extframework.launchermeta.handler.loadVersionManifest
import dev.extframework.launchermeta.handler.metadata
import dev.extframework.launchermeta.handler.parseMetadata
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

class MojangDeobfuscator(
    path: Path
): MinecraftDeobfuscator {
    override val provider: MappingsProvider = MojangMappingProvider(path)
    override val obfuscatedNamespace: String = "mojang:obfuscated"
    override val deobfuscatedNamespace: String =  "mojang:deobfuscated"

    override fun getName(): String {
        return "mojang"
    }
}

internal class MojangMappingProvider(
    private val mappingStore: DataStore<String, Resource>
) : MappingsProvider {
    companion object {
        const val REAL_TYPE: String =  "mojang:deobfuscated"
        const val FAKE_TYPE: String =  "mojang:obfuscated"
    }

    constructor(path: Path) : this(CachingDataStore(MojangMappingAccess(path)))

    override val namespaces: Set<String> = setOf(REAL_TYPE, FAKE_TYPE)

    override fun forIdentifier(identifier: String): ArchiveMapping {
        val mappingData = mappingStore[identifier] ?: result {
            val manifest = loadVersionManifest()
            val version = manifest.find(identifier)
                ?: throw IllegalArgumentException("Unknown minecraft version for mappings: '$identifier'")
            val m = parseMetadata(version.metadata().merge()).merge().clientMappings().merge()
            mappingStore.put(identifier, m)
            m
        }.getOrThrow()

        return ProGuardMappingParser(FAKE_TYPE, REAL_TYPE).parse(mappingData.openStream())
    }
}

private class MojangMappingAccess(
    private val path: Path
) : DataAccess<String, Resource> {
    override fun read(key: String): Resource? {
        val versionPath = path resolve "client-mappings-$key.json"

        if (!versionPath.exists()) return null

        return versionPath.toUri().toResource()
    }

    override fun write(key: String, value: Resource) {
        val versionPath = path resolve "client-mappings-$key.json"
        versionPath.deleteIfExists()

        runBlocking {
            value.copyTo(versionPath)
        }
    }
}