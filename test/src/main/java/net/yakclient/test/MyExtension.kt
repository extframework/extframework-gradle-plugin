package dev.extframework.test

import dev.extframework.client.api.Extension
import dev.extframework.extensions.example.tweaker.TweakerEntry
import dev.extframework.extensions.test2.MyExtension2

class MyExtension : Extension() {
    override fun cleanup() {
        println("Cleaning!? Ok!")
    }

    override fun init() {
        println(MyExtension2::class.java.name)
        println("Did we tweak? : '${TweakerEntry.tweaked}")
        println("Initing")
    }
}