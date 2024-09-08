package dev.extframework.gradle.deobf

import dev.extframework.archive.mapper.*
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
    @JvmStatic
    lateinit var none: MinecraftDeobfuscator
        private set

    internal fun setup(path: Path) {
        mojang = MojangDeobfuscator(path)

        fabric = object : MinecraftDeobfuscator {
            override val provider: MappingsProvider = FabricMappingProvider(RawFabricMappingProvider(path))
            override val obfuscatedNamespace: String = "mojang:obfuscated"
            override val deobfuscatedNamespace: String = FabricMappingProvider.INTERMEDIARY_NAMESPACE

            override fun getName(): String = "fabric"
        }
        none = object : MinecraftDeobfuscator {
            override val provider: MappingsProvider = object : MappingsProvider {
                override val namespaces: Set<String> = setOf("mojang:obfuscated")

                override fun forIdentifier(identifier: String): ArchiveMapping {
                    return ArchiveMapping(
                        namespaces,MappingValueContainerImpl(HashMap()), MappingNodeContainerImpl(setOf())
                    )
                }
            }
            override val obfuscatedNamespace: String = "mojang:obfuscated"
            override val deobfuscatedNamespace: String = "mojang:obfuscated"

            override fun getName(): String = "none"
        }
    }
}