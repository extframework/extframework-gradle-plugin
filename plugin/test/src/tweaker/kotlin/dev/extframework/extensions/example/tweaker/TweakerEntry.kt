package dev.extframework.extensions.example.tweaker

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.tweaker.EnvironmentTweaker

class TweakerEntry : EnvironmentTweaker {
    companion object {
        var tweaked = false
            private set
    }

    override fun tweak(environment: ExtensionEnvironment): Job<Unit> = job {
        tweaked = true
    }
}