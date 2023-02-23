package net.yakclient.test.eighteen

import net.yakclient.client.api.annotation.Mixin
import net.yakclient.client.api.annotation.SourceInjection
import net.yakclient.test.nineteen.two.NineteenTwo

class Eighteen {
    fun eighteenStuff() {
        NineteenTwo()

        TODO()
    }
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