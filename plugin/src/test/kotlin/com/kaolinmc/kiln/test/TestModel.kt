package com.kaolinmc.kiln.test

import com.kaolinmc.kiln.tasks.GenerateErm
import com.kaolinmc.tooling.api.extension.ExtensionRuntimeModel
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