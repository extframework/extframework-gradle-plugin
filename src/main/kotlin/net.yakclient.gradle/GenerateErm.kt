package net.yakclient.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateErm : DefaultTask() {
    @get:OutputFile
    val ermPath = (project.buildDir.toPath() resolve "libs" resolve "erm.json").toFile()

    @TaskAction
    fun generateErm() {
        val yakclient = project.extensions.getByName("yakclient") as YakClient

        val ermAsBytes =
            ObjectMapper().registerModule(KotlinModule.Builder().build()).writeValueAsBytes(yakclient.erm)

        ermPath.toPath().make()
        ermPath.writeBytes(ermAsBytes)
    }
}