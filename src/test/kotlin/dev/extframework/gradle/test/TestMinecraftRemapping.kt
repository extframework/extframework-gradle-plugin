package dev.extframework.gradle.test

import BootLoggerFactory
import com.durganmcbroom.jobs.launch
import dev.extframework.gradle.deobf.McpDeobfuscator
import dev.extframework.gradle.minecraft.setupMinecraft
import dev.extframework.gradle.tasks.setupMinecraft
import kotlinx.coroutines.runBlocking
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

    @Test
    fun `Test MC download`() {
        launch(BootLoggerFactory()) {
            runBlocking {
                setupMinecraft(
                    "1.8.9",
                    Path("test-run/mc")
                )().merge()
            }
        }
    }
}