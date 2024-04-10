package net.yakclient.extensions.example.tweaker

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.SuccessfulJob
import net.yakclient.components.extloader.api.environment.ExtLoaderEnvironment
import net.yakclient.components.extloader.api.tweaker.EnvironmentTweaker


public class TweakerEntry : EnvironmentTweaker {
    public companion object {
        public var tweaked = false
            private set
    }

    override fun tweak(environment: ExtLoaderEnvironment): Job<Unit> {
        println("Could have tweaked, but i choose not to.")
        tweaked = true

        return SuccessfulJob { }
    }
}