package dev.extframework.gradle.deobf

import dev.extframework.archive.mapper.MappingsProvider
import dev.extframework.components.extloader.extension.mapping.MojangExtensionMappingProvider
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