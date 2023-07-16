package net.yakclient.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.logging.LogLevel
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

fun Project.registerGenerateErmTask(yakclient: YakClientExtension) =
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

        task.doFirst(object : Action<Task> {
            override fun execute(t: Task) {
                val project = t.project

                val extensionRepositories: List<ExtensionRepository> = project.repositories
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
                        ExtensionRepository(
                            "simple-maven",
                            settings
                        )
                    }
                    .toList()

                fun Sequence<String>.mapDependencies(): List<Map<String, String>> =
                    map(project.configurations::named)
                        .map(NamedDomainObjectProvider<Configuration>::get)
                        .flatMap(Configuration::getDependencies)
                        .mapNotNull(YakClientExtension.Companion::ermDependency)
                        .associateBy {
                            it["descriptor"]
                        } // We do this to filter duplicates, anything that has the same descriptor has to go.
                        .map { it.value }


                val erm = yakclient.erm
                yakclient.partitions
                    .asSequence()
                    .map { "${it.name}Extension" }
                    .mapDependencies()
                    .forEach(erm.extensions::add)

                erm.extensionRepositories.addAll(extensionRepositories.map(ExtensionRepository::settings))

                erm.versionPartitions.addAll(yakclient.partitions.map {
                    ExtensionVersionPartition(
                        it.name,
                        "META-INF/versioning/partitions/${it.name}",
                        it.supportedVersions.toMutableSet(),
                        extensionRepositories.toMutableList(),
                        listOf(
                            it.sourceSet.apiConfigurationName,
                            it.sourceSet.runtimeOnlyConfigurationName,
                            it.sourceSet.implementationConfigurationName
                        ).asSequence().mapDependencies().toMutableList(),
                        mutableListOf()
                    )
                })

                erm.mainPartition = yakclient.partitions.find(VersionPartition::isMain)?.name
                    ?: throw IllegalArgumentException("No main partition set! Set the partition with 'isMain' inside your version partition configuration")
            }
        })
    }
