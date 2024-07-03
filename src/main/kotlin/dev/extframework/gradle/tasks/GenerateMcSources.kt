package dev.extframework.gradle.tasks

import dev.extframework.common.util.resolve
import dev.extframework.gradle.ExtFrameworkExtension
import dev.extframework.gradle.mavenLocal
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateMcSources : DefaultTask() {
    private val basePath = project.mavenLocal()

    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:Input
    abstract val mappingProvider: Property<String>

    @get:OutputFile
    val minecraftOut: RegularFileProperty
        get() = project.objects.fileProperty().convention {
            val version = minecraftVersion.get()
            (basePath resolve "net" resolve "minecraft" resolve "client" resolve version resolve mappingProvider.get() resolve  "minecraft-${version}.jar").toFile()
        }


    @get:OutputDirectory
    val minecraftLibsOut: ConfigurableFileCollection
        get() = project.objects.fileCollection().from(
            run {
                val version = minecraftVersion.get()
                (basePath resolve "net" resolve "minecraft" resolve "client" resolve version resolve mappingProvider.get() resolve "libs").toFile()
            }
        )

    @TaskAction
    fun generateSources() {
        val extframework = project.extensions.getByName("extension") as ExtFrameworkExtension
        setupMinecraft(
            minecraftVersion.orNull
                ?: throw IllegalArgumentException("Minecraft version for minecraft source generation not set! This task name was: '${this.name}'."),
            basePath,
            extframework.mappingProviders.findByName(mappingProvider.get()) ?: throw java.lang.IllegalArgumentException("Unknown mapping provider: '${mappingProvider.get()}'"),
            mappingProvider.get()
        )
    }
}