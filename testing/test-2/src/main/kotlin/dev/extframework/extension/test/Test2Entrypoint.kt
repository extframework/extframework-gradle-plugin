package dev.extframework.extension.test

import dev.extframework.core.entrypoint.Entrypoint
import dev.extframework.core.minecraft.MinecraftTweaker
import dev.extframework.extensions.example.test2.Tweaker2Entry

class Test2Entrypoint : Entrypoint() {
    override fun init() {
        println("Entered")
        Tweaker2Entry()
    }
}