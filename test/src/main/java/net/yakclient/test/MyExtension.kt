package net.yakclient.test

import net.yakclient.client.api.Extension
import net.yakclient.extensions.example.tweaker.TweakerEntry
import net.yakclient.extensions.test2.MyExtension2

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