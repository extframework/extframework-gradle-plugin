package dev.extframework.gradle.tasks

import com.durganmcbroom.resources.Resource
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.extframework.archives.ArchiveReference
import dev.extframework.common.util.resolve
import dev.extframework.extloader.util.emptyArchiveReference
import dev.extframework.gradle.ExtFrameworkExtension
import dev.extframework.gradle.MutableExtensionRuntimeModel
import dev.extframework.gradle.util.ListPropertySerializer
import dev.extframework.gradle.util.MapPropertySerializer
import dev.extframework.gradle.util.PropertySerializer
import dev.extframework.gradle.util.SetPropertySerializer
import dev.extframework.gradle.write
import org.gradle.api.DefaultTask
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

abstract class BuildBundle : DefaultTask() {
    private val extframework
        get() = project.extensions.getByName("extension") as ExtFrameworkExtension

    @get:OutputFile
    val bundlePath: File =
        (project.layout.buildDirectory.asFile.get().toPath() resolve "libs" resolve "extension.bundle").toFile()

    @TaskAction
    fun generateErm() {
        val mapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(
                SimpleModule()
                    .addSerializer(Property::class.java, PropertySerializer())
                    .addSerializer(SetProperty::class.java, SetPropertySerializer())
                    .addSerializer(MapProperty::class.java, MapPropertySerializer())
                    .addSerializer(ListProperty::class.java, ListPropertySerializer())
            )

        val erm = buildErm()
        val metadata = extframework.metadata.get()

        val archive = emptyArchiveReference()
        archive.writer.put(
            ArchiveReference.Entry(
                "erm.json",
                Resource("<heap>") {
                    ByteArrayInputStream(mapper.writeValueAsBytes(erm))
                },
                false,
                archive
            )
        )
        archive.writer.put(
            ArchiveReference.Entry(
                "metadata.json",
                Resource("<heap>") {
                    ByteArrayInputStream(mapper.writeValueAsBytes(metadata))
                },
                false,
                archive
            )
        )

        extframework.partitions.forEach { partition ->
            project.tasks.named(partition.sourceSet.jarTaskName).get().outputs.files.forEach { file ->
                archive.writer.put(
                    ArchiveReference.Entry(
                        "partitions/${partition.name}.${file.extension}",
                        Resource("<heap>") {
                            FileInputStream(file)
                        },
                        false,
                        archive
                    )
                )
            }

            project.tasks.named(partition.generatePrmTaskName).get().outputs.files.forEach { file ->
                archive.writer.put(
                    ArchiveReference.Entry(
                        "partitions/${partition.name}.${file.extension}",
                        Resource("<heap>") {
                            FileInputStream(file)
                        },
                        false,
                        archive
                    )
                )
            }
        }

        archive.write(bundlePath.toPath())
    }

    private fun buildErm(): MutableExtensionRuntimeModel {
        extframework.eagerModel {
            it.repositories.addAll(
                project.repositories.map {
                    when (it) {
                        is DefaultMavenLocalArtifactRepository -> mutableMapOf(
                            "location" to it.url.path,
                            "type" to "local"
                        )

                        is DefaultMavenArtifactRepository -> mutableMapOf(
                            "location" to it.url.toString(),
                            "type" to "default"
                        )

                        else -> throw Exception("Unknown repository type: ${it::class}")
                    }
                }
            )
        }

        return extframework.erm.get()
    }
}