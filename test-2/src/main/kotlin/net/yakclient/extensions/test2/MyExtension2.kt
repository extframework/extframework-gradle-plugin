package net.yakclient.extensions.test2

import net.yakclient.client.api.Extension

class MyExtension2 : Extension() {
    override fun cleanup() {
    }

    override fun init() {
        println("INIT!")
    }
}