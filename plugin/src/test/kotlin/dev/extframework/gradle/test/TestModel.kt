package dev.extframework.gradle.test

import dev.extframework.gradle.tasks.GenerateErm
import dev.extframework.tooling.api.extension.ExtensionRuntimeModel
import kotlin.test.Test

class TestModel {
    @Test
    fun `Test write erm`() {
        val model = ExtensionRuntimeModel(
            1,
            "com.example",
            "test",
            "1",
            listOf(),
            setOf(),
            setOf(),
            mapOf()
        )

        println(String(GenerateErm.writeErm(model)))
    }
}