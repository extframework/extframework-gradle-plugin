package dev.extframework.extensions.test2

import dev.extframework.core.api.Extension


class MyExtension2 : Extension() {
    override fun init() {

        println("INIT!")
    }
}