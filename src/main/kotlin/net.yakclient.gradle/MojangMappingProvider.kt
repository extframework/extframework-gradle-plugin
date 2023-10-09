package net.yakclient.gradle

import kotlinx.coroutines.runBlocking
import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.parsers.ProGuardMappingParser
import net.yakclient.boot.store.CachingDataStore
import net.yakclient.boot.store.DataAccess
import net.yakclient.boot.store.DataStore
import net.yakclient.common.util.copyTo
import net.yakclient.common.util.resolve
import net.yakclient.common.util.resource.SafeResource
import net.yakclient.common.util.toResource
import net.yakclient.internal.api.mapping.MappingsProvider
import net.yakclient.launchermeta.handler.clientMappings
import net.yakclient.launchermeta.handler.loadVersionManifest
import net.yakclient.launchermeta.handler.metadata
import net.yakclient.launchermeta.handler.parseMetadata
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

class MojangMappingProvider(
//    private val mappingStore: DataStore<String, SafeResource>
) : MappingsProvider {
    companion object {
        const val REAL_TYPE: String =  "mojang/deobfuscated"
        const val FAKE_TYPE: String =  "mojang/obfuscated"
    }

//    constructor(path: Path) : this(CachingDataStore(MojangMappingAccess(path)))

    override val realType: String = REAL_TYPE
    override val fakeType: String = FAKE_TYPE

    override fun forIdentifier(identifier: String): ArchiveMapping {
//        val mappingData = mappingStore[identifier] ?: run {
            val manifest = loadVersionManifest()
            val version = manifest.find(identifier)
                ?: throw IllegalArgumentException("Unknown minecraft version for mappings: '$identifier'")
            val m = parseMetadata(version.metadata()).clientMappings()
//            mappingStore.put(identifier, m)
//            m
//        }

        return  ProGuardMappingParser.parse(m.open())

    }
}

private class MojangMappingAccess(
    private val path: Path,
    private val type: Path,
) : DataAccess<String, SafeResource> {


    override fun read(key: String): SafeResource? {
        val versionPath = path resolve "net" resolve "minecraft" resolve "client" resolve "client-mappings-$key.json"

        if (!versionPath.exists()) return null

        return versionPath.toUri().toResource()
    }

    override fun write(key: String, value: SafeResource) {
        val versionPath = path resolve "client-mappings-$key.json"
        versionPath.deleteIfExists()

        runBlocking {
            value.copyTo(versionPath)
        }
    }
}