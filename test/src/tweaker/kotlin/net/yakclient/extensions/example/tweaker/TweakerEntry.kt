package net.yakclient.extensions.example.tweaker

import net.yakclient.components.extloader.api.environment.ExtLoaderEnvironment
import net.yakclient.components.extloader.api.tweaker.EnvironmentTweaker
import net.yakclient.extensions.test2.TweakerTest2


public class TweakerEntry : EnvironmentTweaker {
    public companion object {
        public var tweaked = false
            private set
    }

    override fun tweak(environment: ExtLoaderEnvironment) {
        println("Could have tweaked, but i choose not to.")
       println(TweakerTest2.something)
        tweaked = true
    }
}