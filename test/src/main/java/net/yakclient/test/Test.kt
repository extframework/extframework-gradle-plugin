package net.yakclient.test

import net.yakclient.client.api.annotation.Mixin
import net.yakclient.client.api.annotation.SourceInjection
import javax.annotation.processing.AbstractProcessor

class Test {

}


fun main(args: Array<String>) {
    println("YAY")
}

@Mixin("asdfasdf")
class Injection() {
    @SourceInjection(
        point = "asdf",
        from = "asdf",
        to = "asdf",
        methodFrom = "asdf",
        methodTo = "asdaaf",
        priority = 0
    )
    fun something() {

    }
}