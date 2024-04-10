package net.yakclient.gradle

import net.yakclient.common.util.immutableLateInit
import java.nio.file.Path

object MinecraftMappings {
    @JvmStatic
    lateinit var mojang : MinecraftDeobfuscator
        private set

    internal fun setup(path: Path) {
        mojang = MojangDeobfuscator(path)
    }
}