package net.yakclient.extensions.test2

import mezz.jei.api.constants.ModIds
import net.yakclient.client.api.Extension

class MyExtension2 : Extension() {
    override fun cleanup() {}

    override fun init() {
        println(ModIds.MINECRAFT_NAME + " hasdhfhadshfhdasf")
        println("INIT!")
    }
}