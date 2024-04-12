package net.yakclient.gradle.tasks

import net.yakclient.archive.mapper.findShortest
import net.yakclient.archive.mapper.newMappingsGraph
import net.yakclient.archive.mapper.transform.transformArchive
import net.yakclient.archives.Archives
import net.yakclient.gradle.YakClientExtension
import net.yakclient.gradle.write
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject
import kotlin.io.path.toPath

abstract class RemapTask : DefaultTask() {
    @get:InputFiles
    abstract val inputOutputFiles: ConfigurableFileCollection

    @get:Input
    abstract val sourceNamespace: Property<String>

    @get:Input
    abstract val targetNamespace: Property<String>

    @get:Input
    abstract val mappingIdentifier: Property<String>

    @TaskAction
    fun remap() {
        val yakClientExtension: YakClientExtension = project.extensions.getByType(YakClientExtension::class.java)

        val graph = newMappingsGraph(yakClientExtension.mappingProviders.map { it.provider })
        val mappings = graph.findShortest(
            sourceNamespace.get(),
            targetNamespace.get()
        )

        val archives = inputOutputFiles.files.filter {
            it.extension == "jar"
        }.map {
            Archives.find(it.toPath(), Archives.Finders.ZIP_FINDER)
        }

        archives.forEach {
            transformArchive(
                it,
                archives,
                mappings.forIdentifier(mappingIdentifier.get()),
                sourceNamespace.get(),
                targetNamespace.get()
            )

            it.write(
                it.location.toPath()
            )
        }
    }
}

