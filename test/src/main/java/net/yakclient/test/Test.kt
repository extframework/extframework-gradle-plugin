package net.yakclient.test

import net.yakclient.client.api.annotation.Mixin
import net.yakclient.client.api.annotation.SourceInjection
import javax.annotation.processing.AbstractProcessor

class Test {

}


fun main(args: Array<String>) {
    println("YAY")
}

@Mixin("OHTTHER OTHER")
class OtherInjection() {
    @SourceInjection(
        point = "idk",
        from = "idk",
        to = "idk",
        methodFrom = "idk",
        methodTo = "idk",
        priority = 100000
    )
    fun something() {

    }
}