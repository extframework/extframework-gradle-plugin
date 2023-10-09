package net.yakclient.extensions.example.tweaker

import net.yakclient.internal.api.environment.ExtLoaderEnvironment
import net.yakclient.internal.api.tweaker.EnvironmentTweaker

public class TweakerEntry : EnvironmentTweaker {
    public companion object {
        public var tweaked = false
            private set
    }

    override fun tweak(environment: ExtLoaderEnvironment): ExtLoaderEnvironment {
        println("Could have tweaked, but i choose not to.")
        tweaked = false
        return environment
    }
}