package dev.extframework.extensions.test2

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.internal.api.environment.ExtensionEnvironment
import dev.extframework.internal.api.tweaker.EnvironmentTweaker

class TweakerTest2 : EnvironmentTweaker {
    override fun tweak(environment: ExtensionEnvironment): Job<Unit> = job {
        println("Test test tweaker ran!")
    }

    companion object {
        public val something = 5
    }
}