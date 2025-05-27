package dev.extframework.extension.test2

import dev.extframework.core.entrypoint.Entrypoint
import dev.extframework.extensions.example.test2.Tweaker2Entry

class Class : Entrypoint() {
    override fun init() {
        println("Ok i am here")
        Tweaker2Entry()
//        net.minecraft.client.Minecraft
    }

    init {
    }
}