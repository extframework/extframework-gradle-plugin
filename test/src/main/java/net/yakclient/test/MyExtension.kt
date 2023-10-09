package net.yakclient.test

import net.yakclient.client.api.Extension
import net.yakclient.extensions.example.tweaker.TweakerEntry

class MyExtension : Extension() {
    override fun cleanup() {
        println("Cleaning!? Ok!")
    }

    override fun init() {
        println("Did we tweak? : '${TweakerEntry.tweaked}")
        println("Initing")
    }
}