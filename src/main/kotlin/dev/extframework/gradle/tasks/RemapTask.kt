package dev.extframework.gradle.tasks

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import dev.extframework.archive.mapper.findShortest
import dev.extframework.archive.mapper.newMappingsGraph
import dev.extframework.archive.mapper.transform.transformArchive
import dev.extframework.archives.Archives
import dev.extframework.gradle.ExtFrameworkExtension
import dev.extframework.gradle.write
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import kotlin.io.path.name
import kotlin.io.path.toPath


abstract class RemapTask : DefaultTask() {
    @get:InputFiles
    abstract val input: ConfigurableFileCollection

    @get:Input
    abstract val sourceNamespace: Property<String>

    @get:Input
    abstract val targetNamespace: Property<String>

    @get:Input
    abstract val mappingIdentifier: Property<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun remap() {
        val extension: ExtFrameworkExtension = project.extensions.getByType(ExtFrameworkExtension::class.java)

        val graph = newMappingsGraph(extension.mappingProviders.map { it.provider })
        val mappingsProvider = graph.findShortest(
            sourceNamespace.get(),
            targetNamespace.get()
        )
        val mappings = mappingsProvider.forIdentifier(mappingIdentifier.get())

        val archives = input.asFileTree.files.filter {
            it.extension == "jar"
        }.map {
            Archives.find(it.toPath(), Archives.Finders.ZIP_FINDER)
        }

        runBlocking {
            archives.map {
                async {
                    transformArchive(
                        it,
                        archives,
                        mappings,
                        sourceNamespace.get(),
                        targetNamespace.get()
                    )

                    it.write(
                        output.file(it.location.toPath().name).get().asFile.toPath()
                    )
                }
            }.onEach {
                it.start()
            }.forEach {
                it.join()
            }
        }
    }
}