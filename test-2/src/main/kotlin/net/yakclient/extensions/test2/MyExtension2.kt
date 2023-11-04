package net.yakclient.extensions.test2

import net.yakclient.client.api.Extension
import org.apache.log4j.NDC

class MyExtension2 : Extension() {
    override fun cleanup() {}

    override fun init() {
        println(NDC.get())
        println("INIT!")
    }
}