package net.yakclient.gradle

import net.yakclient.components.extloader.api.mapping.MappingsProvider
import org.gradle.api.Named

interface MinecraftDeobfuscator : Named {
    val provider: MappingsProvider

    val obfuscatedNamespace: String
    val deobfuscatedNamespace: String
}