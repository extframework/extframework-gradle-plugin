package net.yakclient.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import groovy.lang.Closure
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.tasks.PropertyFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.TaskAction

abstract class GenerateErm : DefaultTask() {
    @get:OutputFile
    val ermPath = (project.buildDir.toPath() resolve "libs" resolve "erm.json").toFile()

    @get:Input
    abstract val inputFiles: Property<FileCollection>

    override fun doLast(action: Action<in Task>): Task {
        return super.doLast(action)
    }

    @TaskAction
    fun generateErm() {
        val yakclient = project.extensions.getByName("yakclient") as YakClientExtension

        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

        val mixins = inputFiles.get().asFileTree
            .filter { it.name == "mixin-annotations.json" }
            .flatMap {
                mapper.readValue<List<ExtensionMixin>>(it.readBytes())
            }

        yakclient.erm.mixins += mixins
        val ermAsBytes =
            ObjectMapper().registerModule(KotlinModule.Builder().build()).writeValueAsBytes(yakclient.erm)

        ermPath.toPath().make()
        ermPath.writeBytes(ermAsBytes)
    }
}