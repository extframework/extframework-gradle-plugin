package dev.extframework.gradle.tasks

import GenerateErm
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.openStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.extframework.archives.ArchiveReference
import dev.extframework.common.util.readInputStream
import dev.extframework.common.util.resolve
import dev.extframework.extloader.util.emptyArchiveReference
import dev.extframework.gradle.ExtFrameworkExtension
import dev.extframework.gradle.MutableExtensionRuntimeModel
import dev.extframework.gradle.util.ListPropertySerializer
import dev.extframework.gradle.util.MapPropertySerializer
import dev.extframework.gradle.util.ProviderSerializer
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
import java.security.MessageDigest
import java.util.HexFormat

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
                    .addSerializer(Property::class.java, ProviderSerializer())
                    .addSerializer(SetProperty::class.java, SetPropertySerializer())
                    .addSerializer(MapProperty::class.java, MapPropertySerializer())
                    .addSerializer(ListProperty::class.java, ListPropertySerializer())
            )

        val metadata = extframework.metadata.get()

        val archive = emptyArchiveReference()

        archive.writer.put(
            ArchiveReference.Entry(
                "erm.json",
                Resource("<heap>") {
                    FileInputStream(project.tasks.withType(GenerateErm::class.java).getByName("generateErm").ermPath)
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
                        "${partition.name}.${file.extension}",
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
                        "${partition.name}.${file.extension}",
                        Resource("<heap>") {
                            FileInputStream(file)
                        },
                        false,
                        archive
                    )
                )
            }
        }

        val entries = archive.reader.entries().toMutableList()
        listOf("md5", "sha1", "sha256", "sha512").forEach {
            computeHashes(entries, it)
        }

        archive.write(bundlePath.toPath())
    }

    private fun computeHashes(
        entries: List<ArchiveReference.Entry>,
        _hashType: String
    ) {
        val hashType = _hashType.lowercase()

        val engine = MessageDigest.getInstance(hashType)

        entries
            .filterNot { it.isDirectory }
            .forEach { entry ->
                val digest = engine.digest(
                    entry.resource.openStream().readInputStream(),
                )

                entry.handle.writer.put(
                    ArchiveReference.Entry(
                        entry.name + "." + hashType,
                        Resource("<heap>") {
                            HexFormat.of().formatHex(digest).byteInputStream()
                        },
                        false,
                        entry.handle
                    )
                )

                engine.reset()
            }
    }
}