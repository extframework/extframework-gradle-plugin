package net.yakclient.test

import net.yakclient.client.api.Extension
import net.yakclient.client.api.ExtensionContext

class MyExtension : Extension() {
    override fun cleanup() {
        println("Cleaning!?")
    }

    override fun init(context: ExtensionContext) {
        println("Initting")
    }
}