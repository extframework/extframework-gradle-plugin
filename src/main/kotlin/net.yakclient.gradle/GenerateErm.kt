package net.yakclient.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateErm : DefaultTask() {
    @get:OutputFile
    val ermPath = (project.buildDir.toPath() resolve "libs" resolve "erm.json").toFile()

    @get:Input
    abstract val preprocessorOutput: MapProperty<String, FileCollection> // Partition name to file

    override fun doLast(action: Action<in Task>): Task {
        return super.doLast(action)
    }

    @TaskAction
    fun generateErm() {
        val yakclient = project.extensions.getByName("yakclient") as YakClientExtension

        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

        fun <K, V, T> Map<K, V>.mapValuesNotNull(transform: (Map.Entry<K, V>) -> T?): Map<K, T> {
            return this.mapValues(transform)
                .mapNotNull { p -> p.value?.let { p.key to it } }
                .toMap()
        }

        val allMixins =
            preprocessorOutput.get().mapValuesNotNull { (_, files) ->
                val file = files.asFileTree.find { it.name.contains("mixin-annotations.json") }

                file?.readBytes()?.let {
                    mapper.readValue<List<ExtensionMixin>>(it)
                }
            }

        yakclient.erm.versionPartitions.forEach {
            val mixins = allMixins[it.name] ?: return@forEach
            it.mixins += mixins
        }

        val ermAsBytes =
            ObjectMapper().registerModule(KotlinModule.Builder().build()).writeValueAsBytes(yakclient.erm)

        ermPath.toPath().make()
        ermPath.writeBytes(ermAsBytes)
    }
}