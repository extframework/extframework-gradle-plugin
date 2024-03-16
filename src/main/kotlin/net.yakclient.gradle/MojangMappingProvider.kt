package net.yakclient.gradle

import com.durganmcbroom.jobs.launch
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.openStream
import com.durganmcbroom.resources.toResource
import net.yakclient.archive.mapper.ArchiveMapping
import net.yakclient.archive.mapper.parsers.proguard.ProGuardMappingParser
import net.yakclient.boot.store.DataAccess
import net.yakclient.common.util.copyTo
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.api.mapping.MappingsProvider
import net.yakclient.launchermeta.handler.clientMappings
import net.yakclient.launchermeta.handler.loadVersionManifest
import net.yakclient.launchermeta.handler.metadata
import net.yakclient.launchermeta.handler.parseMetadata
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

class MojangMappingProvider(
) : MappingsProvider {
    companion object {
        const val DEOBF_NS: String = "mojang:deobfuscated"
        const val OBF_NS: String = "mojang:obfuscated"

    }

//    constructor(path: Path) : this(CachingDataStore(MojangMappingAccess(path)))

    override val namespaces: Set<String> = setOf(DEOBF_NS, OBF_NS)

    override fun forIdentifier(identifier: String): ArchiveMapping = launch {
        val manifest = loadVersionManifest()
        val version = manifest.find(identifier)
            ?: throw IllegalArgumentException("Unknown minecraft version for mappings: '$identifier'")
        val m = parseMetadata(version.metadata().merge()).merge().clientMappings().merge()

        ProGuardMappingParser(OBF_NS, DEOBF_NS).parse(m.openStream())
    }
}

private class MojangMappingAccess(
    private val path: Path,
    private val type: Path,
) : DataAccess<String, Resource> {


    override fun read(key: String): Resource? {
        val versionPath = path resolve "net" resolve "minecraft" resolve "client" resolve "client-mappings-$key.json"

        if (!versionPath.exists()) return null

        return versionPath.toResource()
    }

    override fun write(key: String, value: Resource) {
        val versionPath = path resolve "client-mappings-$key.json"
        versionPath.deleteIfExists()

        value.copyTo(versionPath)
    }
}