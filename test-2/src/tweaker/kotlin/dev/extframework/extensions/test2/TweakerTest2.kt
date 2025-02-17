package dev.extframework.extensions.test2

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.tweaker.EnvironmentTweaker

class TweakerTest2 : EnvironmentTweaker {
    override fun tweak(environment: ExtensionEnvironment): Job<Unit> = job() {
        println("here" + environment)
//        TODO("Not yet implemented")
    }

    companion object {
        public val something = 5
    }
}