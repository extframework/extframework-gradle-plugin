package dev.extframework.extensions.example.tweaker

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.extensions.example.test2.Tweaker2Entry
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.tweaker.EnvironmentTweaker
import dev.extframework.library.LibraryClass
import dev.extframework.library.OtherLibClass

class TweakerEntry : EnvironmentTweaker {
    companion object {
        var tweaked = false
            private set
    }

    override fun tweak(environment: ExtensionEnvironment): Job<Unit> = job {
        LibraryClass().hello()
        LibraryClass()
        OtherLibClass()
        Tweaker2Entry()
        tweaked = true
    }
}