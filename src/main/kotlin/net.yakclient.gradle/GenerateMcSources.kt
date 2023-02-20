package net.yakclient.gradle

import net.yakclient.common.util.resolve
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import java.nio.file.Path

abstract class GenerateMcSources : DefaultTask() {
    private val basePath = Path.of(project.repositories.mavenLocal().url)

    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:OutputFile
    val minecraftOut: RegularFileProperty
        get() = project.objects.fileProperty().convention {
            val version = minecraftVersion.get()
            (basePath resolve "net" resolve "minecraft" resolve "client" resolve version resolve "minecraft-${version}.jar").toFile()
        }


    @get:OutputDirectory
    val minecraftLibsOut: ConfigurableFileCollection
        get() = project.objects.fileCollection().from(
            run {
                val version = minecraftVersion.get()
                (basePath resolve "net" resolve "minecraft" resolve "client" resolve version resolve "libs").toFile()
            }
        )

    @TaskAction
    fun generateSources() {
        setupMinecraft(
            minecraftVersion.orNull
                ?: throw IllegalArgumentException("Minecraft version for minecraft source generation not set! This task name was: '${this.name}'."),
            basePath
        )
    }
}