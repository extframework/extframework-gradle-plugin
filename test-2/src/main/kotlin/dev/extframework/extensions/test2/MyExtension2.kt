package dev.extframework.extensions.test2

import dev.extframework.core.entrypoint.Entrypoint


class MyExtension2 : Entrypoint() {
    override fun init() {
        println("INIT!")
    }
}