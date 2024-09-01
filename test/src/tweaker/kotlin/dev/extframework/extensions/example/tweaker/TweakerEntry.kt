package dev.extframework.extensions.example.tweaker

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.SuccessfulJob
import dev.extframework.internal.api.environment.ExtensionEnvironment
import dev.extframework.internal.api.tweaker.EnvironmentTweaker


public class TweakerEntry : EnvironmentTweaker {
    public companion object {
        public var tweaked = false
            private set
    }

    override fun tweak(environment: ExtensionEnvironment): Job<Unit> {
        println("Could have tweaked, but i choose not to.")
        tweaked = true

        return SuccessfulJob { }
    }
}