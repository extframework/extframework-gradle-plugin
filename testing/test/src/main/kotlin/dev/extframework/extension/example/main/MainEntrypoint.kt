package dev.extframework.extension.example.main

import dev.extframework.core.entrypoint.Entrypoint
import dev.extframework.extensions.example.tweaker.TweakerEntry
import dev.extframework.library.LibraryClass
import dev.extframework.library.OtherLibClass

class MainEntrypoint : Entrypoint() {
    override fun init() {
        TweakerEntry.tweaked
        OtherLibClass()
        LibraryClass()
    }
}