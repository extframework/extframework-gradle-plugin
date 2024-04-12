package net.yakclient.gradle.deobf

import net.yakclient.archive.mapper.MappingsProvider
import net.yakclient.components.extloader.extension.mapping.MojangExtensionMappingProvider
import java.nio.file.Path

class MojangDeobfuscator(
    path: Path
): MinecraftDeobfuscator {
    override val provider: MappingsProvider = MojangExtensionMappingProvider(path)
    override val obfuscatedNamespace: String = MojangExtensionMappingProvider.FAKE_TYPE
    override val deobfuscatedNamespace: String = MojangExtensionMappingProvider.REAL_TYPE

    override fun getName(): String {
        return "mojang"
    }
}