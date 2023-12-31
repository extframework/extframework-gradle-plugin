package net.yakclient.gradle

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.*
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File


abstract class GenerateErm : DefaultTask() {
    private val yakclient
        get() = project.extensions.getByName("yakclient") as YakClientExtension

    @get:OutputFile
    val ermPath: Provider<File> = yakclient.erm.map {
        (project.buildDir.toPath() resolve "libs" resolve "erm.json").toFile()
    }

    @get:Input
    abstract val preprocessorOutput: MapProperty<String, FileCollection> // Partition name to file

    @TaskAction
    fun generateErm() {
        val mapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(
                SimpleModule()
                    .addSerializer(Property::class.java, PropertySerializer())
                    .addSerializer(SetProperty::class.java, SetPropertySerializer())
            )

//        fun <K, V, T> Map<K, V>.mapValuesNotNull(transform: (Map.Entry<K, V>) -> T?): Map<K, T> {
//            return this.mapValues(transform)
//                .mapNotNull { p -> p.value?.let { p.key to it } }
//                .toMap()
//        }

//        val allMixins =
//            preprocessorOutput.get().mapValuesNotNull { (_, files) ->
//                val file = files.asFileTree.find { it.name.contains("mixin-annotations.json") }
//
//                file?.readBytes()?.let {
//                    mapper.readValue<List<MutableExtensionMixin>>(it)
//                }
//            }

//        yakclient.model { erm ->
//            erm.versionPartitions.forEach {
//                val mixins = allMixins[it.name] ?: return@forEach
//                it.mixins.addAll(mixins)
//            }
//        }

        val ermAsBytes =
            mapper.writeValueAsBytes(yakclient.erm.get())

        ermPath.get().toPath().make()
        ermPath.get().writeBytes(ermAsBytes)
    }
}


private class SetPropertySerializer : JsonSerializer<SetProperty<*>>() {
    override fun serialize(value: SetProperty<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value.isPresent) {
            gen.writeObject(value.get())
        } else {
            gen.writeStartArray()
            gen.writeEndArray()
        }
    }
}

private class PropertySerializer : JsonSerializer<Property<*>>() {
    override fun serialize(value: Property<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        if (value.isPresent) {
            gen.writeObject(value.get())
        } else {
            gen.writeNull()
        }
    }
}

internal fun Project.registerGenerateErmTask(yakclient: YakClientExtension) =
    project.tasks.register("generateErm", GenerateErm::class.java) { task ->
        val sourceSets = yakclient.sourceSets

        sourceSets.forEach {
            task.dependsOn(project.tasks.named(it.classesTaskName).get())
        }

        task.preprocessorOutput.set(
            sourceSets
                .associateBy { it.name }
                .mapValues { project.files(it.value.output.files) }
        )

        task.doFirst { t ->
            val project = t.project

            val extensionRepositories: List<MutableExtensionRepository> = project.repositories
                .asSequence()
                .onEach {
                    if (it !is MavenArtifactRepository) logger.log(
                        LogLevel.WARN,
                        "Repository: '${it.name}' is not a Maven repository and so is not currently supported in extensions."
                    )

                    if (it is DefaultMavenLocalArtifactRepository)
                        logger.log(
                            LogLevel.WARN,
                            "Maven repository: '${it.name}' at '${it.url}' is a local repo. While currently supported, this is a bad practice and should be removed in production builds."
                        )
                }
                .filterIsInstance<MavenArtifactRepository>()
                .associateBy { it.url }
                .map { it.value }
                .map {
                    if (it.credentials.password != null || it.credentials.username != null)
                        throw UnsupportedOperationException("Using credentials in repositories are not supported yet!")

                    val b = it is DefaultMavenLocalArtifactRepository
                    val type = if (b) "local" else "default"
                    val location = if (b) it.url.path else it.url.toString()
                    val settings = mutableMapOf(
                        "location" to location.removeSuffix("/"),
                        "type" to type
                    )
                    MutableExtensionRepository(
                        "simple-maven",
                        settings
                    )
                }
                .toList()

            fun Sequence<String>.mapDependencies(): List<MutableMap<String, String>> =
                map(project.configurations::named)
                    .map(NamedDomainObjectProvider<Configuration>::get)
                    .flatMap(Configuration::getDependencies)
                    .mapNotNull(YakClientExtension.Companion::ermDependency)
                    .associateBy {
                        it["descriptor"]
                    } // We do this to filter duplicates, anything that has the same descriptor has to go.
                    .filterNot { it.key?.startsWith("net.yakclient:client-api") == true }
                    .map { it.value.toMutableMap() }

            yakclient.model { erm ->
                yakclient.partitions
                    .asSequence()
                    .map { "${it.name}Extension" }
                    .mapDependencies()
                    .forEach(erm.extensions::add)

                erm.extensionRepositories.addAll(extensionRepositories.map(MutableExtensionRepository::settings))
                erm.extensions.addAll(
                    listOf(
                        yakclient.extensionConfiguration.name,
                    ).asSequence().mapDependencies().toMutableList()
                )

                erm.versionPartitions.addAll(yakclient.partitions.map { versionPartition ->
                    MutableExtensionVersionPartition(
                        project.property {
                            versionPartition.name
                        },
                        project.property {
                            "META-INF/versioning/partitions/${versionPartition.name}"
                        },
                        project.objects.property(String::class.java).convention(versionPartition.mappingsType.map {
                            yakclient.mappingProviders.getByName(it).deobfuscatedNamespace
                        }),
                        versionPartition.supportedVersions,
                        project.newSetProperty {
                            extensionRepositories.toMutableSet()
                        },
                        project.newSetProperty {
                            listOf(
                                versionPartition.dependencies.includeConfiguration.name,
                            ).asSequence().mapDependencies().toMutableSet()
                        }
                    )
                })

                erm.mainPartition.update { provider ->
                    provider.map {
                        it.path.set("")
                        it.repositories.addAll(extensionRepositories)
                        it.dependencies.addAll(
                            listOf(
                                MAIN_INCLUDE_CONFIGURATION_NAME,
                            ).asSequence().mapDependencies().toMutableList()
                        )

                        it
                    }
                }

                if (yakclient.tweakerPartition.isPresent) {
                    erm.tweakerPartition.convention(
                        MutableExtensionTweakerPartition(
                            project.property { "tweaker" },
                            project.property { "META-INF/versioning/partitions/tweaker" },
                            project.newSetProperty { extensionRepositories.toMutableSet() },
                            project.newSetProperty {
                                listOf(
                                    TWEAKER_INCLUDE_CONFIGURATION_NAME,
                                ).asSequence().mapDependencies().toMutableSet()
                            },
                            yakclient.tweakerPartition.get().entrypoint
                        )
                    )
                }
            }
        }
    }
