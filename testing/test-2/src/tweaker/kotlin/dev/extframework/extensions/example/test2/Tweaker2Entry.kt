package dev.extframework.extensions.example.test2

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.tweaker.EnvironmentTweaker

class Tweaker2Entry : EnvironmentTweaker {
    override fun tweak(environment: ExtensionEnvironment): Job<Unit> = job {
    }
}