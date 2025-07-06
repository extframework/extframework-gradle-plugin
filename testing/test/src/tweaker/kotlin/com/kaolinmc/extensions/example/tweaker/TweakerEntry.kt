package com.kaolinmc.extensions.example.tweaker

import com.kaolinmc.extensions.example.test2.Tweaker2Entry
import com.kaolinmc.tooling.api.environment.ExtensionEnvironment
import com.kaolinmc.tooling.api.tweaker.EnvironmentTweaker

class TweakerEntry : EnvironmentTweaker {
    companion object {
        var tweaked = false
            private set
    }

    override fun tweak(environment: ExtensionEnvironment) {
        Tweaker2Entry()
        tweaked = true
    }
}