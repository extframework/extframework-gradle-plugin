package com.kaolinmc.extension.test2

import com.kaolinmc.core.entrypoint.Entrypoint
import com.kaolinmc.extensions.example.test2.Tweaker2Entry

class Class : Entrypoint() {
    override fun init() {
        println("Ok i am here")
        Tweaker2Entry()
//        net.minecraft.client.Minecraft
    }

    init {
    }
}