package dev.extframework.gradle.test

import dev.extframework.common.util.readInputStream
import dev.extframework.gradle.config.parseTomlConfig
import kotlin.test.Test

class TestTomlConfiguration {
    @Test
    fun `Test configuration parsing`() {
        val ins = this::class.java.getResourceAsStream("/extension.toml")!!

        println(parseTomlConfig(String(ins.readInputStream())))
    }
}

