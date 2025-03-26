package dev.extframework.extension.example.main

import dev.extframework.core.entrypoint.Entrypoint
import dev.extframework.extensions.example.tweaker.TweakerEntry

class MainEntrypoint : Entrypoint() {
    override fun init() {
        TweakerEntry.tweaked
    }
}