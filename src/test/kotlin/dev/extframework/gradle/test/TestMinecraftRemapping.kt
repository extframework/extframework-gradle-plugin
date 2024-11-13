package dev.extframework.gradle.test

import dev.extframework.gradle.deobf.McpDeobfuscator
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class TestMinecraftRemapping {
    @Test
    fun `Test mcp version fetch minecraft`() {
        println(McpDeobfuscator(Path("test-run")).fetchMcpVersion("1.8.9"))
    }

    @Test
    fun `Test mcp mapping reoslution`() {
      val mappings =  McpDeobfuscator(Path("test-run")).provider.forIdentifier("1.8.9")
        println("here")
    }
}