package com.kaolinmc.kiln.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.kaolinmc.archives.ArchiveReference
import com.kaolinmc.common.util.Hex
import com.kaolinmc.common.util.readInputStream
import com.kaolinmc.common.util.resolve
import com.kaolinmc.extloader.util.emptyArchiveReference
import com.kaolinmc.kiln.api.KaolinExtension
import com.kaolinmc.kiln.util.ListPropertySerializer
import com.kaolinmc.kiln.util.MapPropertySerializer
import com.kaolinmc.kiln.util.ProviderSerializer
import com.kaolinmc.kiln.util.SetPropertySerializer
import com.kaolinmc.kiln.util.write
import org.gradle.api.DefaultTask
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

abstract class BuildBundle : DefaultTask() {
    private val extension
        get() = project.extensions.getByName("extension") as KaolinExtension

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

        val metadata = extension.metadata

        val archive = emptyArchiveReference()

        archive.writer.put(
            ArchiveReference.Entry(
                "erm.json",
                false,
                archive
            ) {
                FileInputStream(project.tasks.withType(GenerateErm::class.java).getByName("generateErm").ermPath)
            }
        )
        archive.writer.put(
            ArchiveReference.Entry(
                "metadata.json",
                false,
                archive
            ) {
                ByteArrayInputStream(mapper.writeValueAsBytes(metadata))
            }
        )

        extension.partitions.forEach { partition ->
            project.tasks.named(partition.sourceSet.jarTaskName).get().outputs.files.forEach { file ->
                archive.writer.put(
                    ArchiveReference.Entry(
                        "${partition.name}.${file.extension}",
                        false,
                        archive
                    ) {
                        FileInputStream(file)
                    }
                )
            }
            project.tasks.named(partition.sourceSet.sourcesJarTaskName).get().outputs.files.forEach { file ->
                archive.writer.put(
                    ArchiveReference.Entry(
                        "${partition.name}-sources.${file.extension}",
                        false,
                        archive
                    ) {
                        FileInputStream(file)
                    }
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
                    entry.open().readInputStream(),
                )

                entry.handle.writer.put(
                    ArchiveReference.Entry(
                        entry.name + "." + hashType,
                        false,
                        entry.handle
                    ) {
                        Hex.formatHex(digest).byteInputStream()
                    }
                )

                engine.reset()
            }
    }
}