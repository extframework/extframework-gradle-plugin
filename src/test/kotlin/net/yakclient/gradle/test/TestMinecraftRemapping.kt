package net.yakclient.gradle.test

import net.yakclient.gradle.setupMinecraft
import org.junit.jupiter.api.Test
import java.nio.file.Path

class TestMinecraftRemapping {
    @Test
    fun `Test remap minecraft`() {
        setupMinecraft("1.19.2", Path.of("/Users/durgan/IdeaProjects/yakclient/yakclient-gradle/asdf"))

    }

}