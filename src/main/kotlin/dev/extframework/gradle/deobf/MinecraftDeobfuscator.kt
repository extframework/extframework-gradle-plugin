package dev.extframework.gradle.deobf

import dev.extframework.archive.mapper.MappingsProvider
import org.gradle.api.Named

interface MinecraftDeobfuscator : Named {
    val provider: MappingsProvider

    val obfuscatedNamespace: String
    val deobfuscatedNamespace: String
}