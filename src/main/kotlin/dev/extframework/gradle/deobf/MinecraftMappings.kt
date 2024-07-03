package dev.extframework.gradle.deobf

import dev.extframework.archive.mapper.MappingsProvider
import dev.extframework.components.extloader.extension.mapping.MojangExtensionMappingProvider
import dev.extframework.gradle.fabric.FabricMappingProvider
import dev.extframework.gradle.fabric.RawFabricMappingProvider
import java.nio.file.Path

object MinecraftMappings {
    @JvmStatic
    lateinit var mojang : MinecraftDeobfuscator
        private set
    @JvmStatic
    lateinit var fabric: MinecraftDeobfuscator
        private set

    internal fun setup(path: Path) {
        mojang = MojangDeobfuscator(path)

        fabric = object : MinecraftDeobfuscator {
            override val provider: MappingsProvider = FabricMappingProvider(RawFabricMappingProvider(path))
            override val obfuscatedNamespace: String = MojangExtensionMappingProvider.FAKE_TYPE
            override val deobfuscatedNamespace: String = FabricMappingProvider.INTERMEDIARY_NAMESPACE

            override fun getName(): String = "fabric"
        }
    }
}