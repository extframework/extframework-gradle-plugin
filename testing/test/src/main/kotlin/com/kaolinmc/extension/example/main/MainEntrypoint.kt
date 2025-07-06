package com.kaolinmc.extension.example.main

import com.kaolinmc.core.entrypoint.Entrypoint
import com.kaolinmc.extensions.example.tweaker.TweakerEntry
import com.kaolinmc.library.LibraryClass
import com.kaolinmc.library.OtherLibClass

class MainEntrypoint : Entrypoint() {
    override fun init() {
        TweakerEntry.tweaked
        OtherLibClass()
        LibraryClass()
    }
}