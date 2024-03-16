package net.yakclient.extensions.test2

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import net.yakclient.components.extloader.api.environment.ExtLoaderEnvironment
import net.yakclient.components.extloader.api.tweaker.EnvironmentTweaker

class TweakerTest2 : EnvironmentTweaker {
    override fun tweak(environment: ExtLoaderEnvironment): Job<Unit> = job {
        println("Test test tweaker ran!")
    }

    companion object {
        public val something = 5
    }
}