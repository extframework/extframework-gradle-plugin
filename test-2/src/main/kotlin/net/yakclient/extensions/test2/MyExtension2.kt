package dev.extframework.extensions.test2

import dev.extframework.client.api.Extension

class MyExtension2 : Extension() {
    override fun cleanup() {} 

    override fun init() {

        println("INIT!")
    }
}