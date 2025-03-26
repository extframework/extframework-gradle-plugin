package dev.extframework.extension.test

import dev.extframework.core.entrypoint.Entrypoint

class Test2Entrypoint : Entrypoint() {
    override fun init() {
        println("Entered")
    }
}