package dev.extframework.extensions.example.tweaker

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.SuccessfulJob
import dev.extframework.components.extloader.api.environment.ExtLoaderEnvironment
import dev.extframework.components.extloader.api.tweaker.EnvironmentTweaker


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