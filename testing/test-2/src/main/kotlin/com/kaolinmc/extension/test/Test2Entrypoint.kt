package com.kaolinmc.extension.test

import com.kaolinmc.core.entrypoint.Entrypoint
import com.kaolinmc.core.minecraft.MinecraftTweaker
import com.kaolinmc.extensions.example.test2.Tweaker2Entry

class Test2Entrypoint : Entrypoint() {
    override fun init() {
        println("Entered")
        Tweaker2Entry()
        MinecraftTweaker()
    }
}