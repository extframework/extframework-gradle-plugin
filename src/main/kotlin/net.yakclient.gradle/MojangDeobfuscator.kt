package net.yakclient.gradle

import net.yakclient.archive.mapper.MappingsProvider
import net.yakclient.components.extloader.extension.mapping.MojangExtensionMappingProvider
import java.nio.file.Path

class MojangDeobfuscator(
    path: Path
): MinecraftDeobfuscator {
    override val provider: MappingsProvider = MojangExtensionMappingProvider(path)
    override val obfuscatedNamespace: String = "mojang:obfuscated"
    override val deobfuscatedNamespace: String = "mojang:deobfuscated"

    override fun getName(): String {
        return "mojang"
    }
}