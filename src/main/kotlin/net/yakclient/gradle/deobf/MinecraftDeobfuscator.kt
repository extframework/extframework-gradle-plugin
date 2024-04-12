package net.yakclient.gradle.deobf

import net.yakclient.archive.mapper.MappingsProvider
import org.gradle.api.Named

interface MinecraftDeobfuscator : Named {
    val provider: MappingsProvider

    val obfuscatedNamespace: String
    val deobfuscatedNamespace: String
}