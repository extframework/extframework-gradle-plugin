package net.yakclient.gradle

import groovy.lang.Closure
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.extension.partition.MainPartitionLoader
import net.yakclient.components.extloader.extension.partition.TweakerPartitionLoader
import net.yakclient.components.extloader.extension.partition.VersionedPartitionLoader
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.jvm.tasks.Jar
import org.gradle.util.internal.GUtil
import java.nio.file.Path
import java.util.*
import kotlin.collections.ArrayList

internal const val CLIENT_VERSION = "1.1-SNAPSHOT"
internal const val CLIENT_MAIN_CLASS = "net.yakclient.client.MainKt"
internal val YAKCLIENT_DIR = Path.of(System.getProperty("user.home")) resolve ".yakclient"

class YakClientGradle : Plugin<Project> {
    override fun apply(project: Project) {
        MinecraftMappings.setup(project.layout.projectDirectory.file("mappings").asFile.toPath())

        project.plugins.apply(JvmEcosystemPlugin::class.java)
        val yakclient = project.extensions.create("yakclient", YakClientExtension::class.java, project)

        val generateErm = project.registerGenerateErmTask(yakclient)

        project.tasks.named("jar", Jar::class.java) { jar ->
            jar.dependsOn(generateErm)
            jar.from(generateErm)
            jar.dependsOn(project.tasks.withType(GenerateMcSources::class.java))

            yakclient.partitions.configureEach { partition ->
                jar.from(partition.sourceSet.output) { copy ->
                    copy.into(partition.partition.path)
                }
            }
        }


        project.registerLaunchTask(yakclient, project.tasks.getByName("publishToMavenLocal"))
    }
}

abstract class YakClientExtension(
    internal val project: Project
) {
    internal val partitions = object : NamedDomainPartitionContainer(project.container(PartitionHandler::class.java)) {
        override fun main(action: Action<MainPartitionHandler>) {
            val partition = MutableExtensionPartition(
                MainPartitionLoader.TYPE,
                project.property { "main" },
                project.property { "META-INF/partitions/main" },
                project.newSetProperty(),
                project.newSetProperty(),
                project.newMapProperty()
            )

            model {
                it.partitions.add(partition)
            }

            val toConfigure = ArrayList<() -> Unit>()
            val handler = MainPartitionHandler(
                project,
                partition,
                sourceSets.getByName("main"),
                toConfigure::add
            )

            add(handler)

            action.execute(handler)
            toConfigure.forEach { it() }
        }

        override fun tweaker(action: Action<TweakerPartitionHandler>) {
            val partition = MutableExtensionPartition(
                TweakerPartitionLoader.TYPE,
                project.property { "tweaker" },
                project.property { "META-INF/partitions/tweaker" },
                project.newSetProperty(),
                project.newSetProperty(),
                project.newMapProperty()
            )

            model {
                it.partitions.add(partition)
            }

            val sourceSet = sourceSets.create("tweaker")
            project.dependencies.add("implementation", sourceSet.output)

            val toConfigure = ArrayList<() -> Unit>()
            val handler = TweakerPartitionHandler(
                project,
                partition,
                sourceSet,
                toConfigure::add
            )

            add(handler)

            action.execute(handler)
            toConfigure.forEach { it() }
        }

        override fun version(name: String, action: Action<MinecraftTargetingPartitionHandler>) {
            val partition = MutableExtensionPartition(
                VersionedPartitionLoader.TYPE,
                project.property { name },
                project.property { "META-INF/partitions/$name" },
                project.newSetProperty(),
                project.newSetProperty(),
                project.newMapProperty()
            )

            model {
                it.partitions.add(partition)
            }

            val sourceSet = sourceSets.create(name)
            project.dependencies.add(sourceSet.implementationConfigurationName, sourceSets.getByName("main").output)
            project.dependencies.add(sourceSet.implementationConfigurationName, downloadExtensions.map {
                it.output
            })
            val toConfigure = ArrayList<() -> Unit>()
            val handler = MinecraftTargetingPartitionHandler(
                project,
                partition,
                sourceSet,
                this@YakClientExtension,
                toConfigure::add
            )

            add(handler)

            action.execute(handler)
            toConfigure.forEach { it() }
        }
    }
    internal val sourceSets: SourceSetContainer = project.extensions.getByType(SourceSetContainer::class.java)
    private val downloadExtensions = project.tasks.register("downloadExtensions", DownloadExtensions::class.java) {
        it.configuration.set(extensionConfiguration.name)
    }

    val erm: Property<MutableExtensionRuntimeModel> = project.property {
        MutableExtensionRuntimeModel(
            project.property {
                project.group as String
            },
            project.property {
                project.name
            },
            project.property {
                project.version as String
            },
            project.property {
                "jar"
            },
            project.newSetProperty(),
            project.newSetProperty(),
            project.newSetProperty(),
        )
    }
    private val extensionConfiguration: Configuration = project.configurations.create("extension") {
        it.isCanBeResolved = false
    }
    val mappingProviders = project.container(MinecraftDeobfuscator::class.java)

    init {
        mappingProviders.add(
            MinecraftMappings.mojang
        )

        project.dependencies.add("implementation", downloadExtensions.map {
            it.output
        })
    }

    fun partitions(action: Action<NamedDomainPartitionContainer>) {
        action.execute(partitions)
    }

    fun extensions(action: Action<ExtensionDependencyHandler>) {
        action.execute(object : ExtensionDependencyHandler {
            override fun require(notation: String) {
                val dep = project.dependencies.add(extensionConfiguration.name, notation) ?: return
                model {
                    it.extensions.add( ermDependency(dep))
                }
            }
        })
    }

    fun model(action: Action<MutableExtensionRuntimeModel>) {
        erm.update {
            it.map { erm ->
                action.execute(erm)
                erm
            }
        }
    }

    internal companion object {
        fun ermDependency(dependency: Dependency): Map<String, String>? {
            if (dependency.group?.isNotBlank() != true
                || dependency.name == "unspecified"
                || dependency.version?.isNotBlank() != true
            ) return null

            return mapOf( // Always a good idea to fill out default values even if they are provided just in case libraries update.
                "descriptor" to ("${dependency.group}:${dependency.name}:${dependency.version}"),
                "isTransitive" to "true",
                "includeScopes" to "compile,runtime,import",
                "excludeArtifacts" to ""
            )
        }
    }
}

interface ExtensionDependencyHandler {
    fun require(notation: String)
}

abstract class NamedDomainPartitionContainer(
    delegate: NamedDomainObjectContainer<PartitionHandler<*>>
) : NamedDomainObjectContainer<PartitionHandler<*>> by delegate {
    abstract fun main(action: Action<MainPartitionHandler>)

    abstract fun tweaker(action: Action<TweakerPartitionHandler>)

    abstract fun version(name: String, action: Action<MinecraftTargetingPartitionHandler>)
}

abstract class PartitionHandler<T : PartitionDependencyHandler>(
    project: Project,
    val partition: MutableExtensionPartition,
    val sourceSet: SourceSet,
    val configure: (() -> Unit) -> Unit
) : Named {
    abstract val dependencies: T

    open fun dependencies(action: Action<T>) {
        configure {
            action.execute(dependencies)
        }
    }

    override fun getName(): String {
        return sourceSet.name
    }
}

class MainPartitionHandler(
    project: Project,
    partition: MutableExtensionPartition,
    sourceSet: SourceSet, configure: (() -> Unit) -> Unit
) : PartitionHandler<PartitionDependencyHandler>(project, partition, sourceSet, configure) {
    override val dependencies = PartitionDependencyHandler(
        project.dependencies, sourceSet
    ) {
        YakClientExtension.ermDependency(it)
            ?.toMutableMap()
            ?.let(partition.dependencies::add)
    }

    var extensionClass: String
        get() {
            return partition.options.getting("extension-class").get()
        }
        set(value) {
            partition.options.put("extension-class", value)
        }
}

class TweakerPartitionHandler(
    project: Project,
    partition: MutableExtensionPartition,
    sourceSet: SourceSet, configure: (() -> Unit) -> Unit
) : PartitionHandler<PartitionDependencyHandler>(project, partition, sourceSet, configure) {
    override val dependencies = PartitionDependencyHandler(
        project.dependencies, sourceSet
    ) {
        YakClientExtension.ermDependency(it)
            ?.toMutableMap()
            ?.let(partition.dependencies::add)
    }

    var tweakerClass: String
        get() {
            return partition.options.getting("tweaker-class").get()
        }
        set(value) {
            partition.options.put("tweaker-class", value)
        }
}

class MinecraftTargetingPartitionHandler(
    project: Project,
    partition: MutableExtensionPartition,
    sourceSet: SourceSet,
    val yakClientExtension: YakClientExtension, configure: (() -> Unit) -> Unit,
) : PartitionHandler<VersionPartitionDependencyHandler>(project, partition, sourceSet, configure) {
    override val dependencies: VersionPartitionDependencyHandler by lazy {
        VersionPartitionDependencyHandler(
            project.dependencies,
            sourceSet,
            project,
            mappings.name,
        ) {
            YakClientExtension.ermDependency(it)
                ?.toMutableMap()
                ?.let(partition.dependencies::add)
        }
    }

    var mappings: MinecraftDeobfuscator
        get() {
            return yakClientExtension.mappingProviders.find {
                it.deobfuscatedNamespace == partition.options.getting("mappingNS").orNull
            } ?: throw Exception("Please set your mappings provider in partition: '${sourceSet.name}'")
        }
        set(value) {
            partition.options.put("mappingNS", value.deobfuscatedNamespace)
        }
    var supportedVersions: List<String>
        get() {
            return partition.options.getting("versions").orNull?.split(",") ?: listOf()
        }
        set(value) {
            partition.options.put("versions", value.joinToString(separator = ","))
        }

    fun supportVersions(vararg versions: String) {
        supportedVersions += versions.toList()
    }
}

open class PartitionDependencyHandler(
    private val delegate: DependencyHandler,
    private val sourceSet: SourceSet,
    private val addDependency: (Dependency) -> Unit
) : DependencyHandler by delegate {
    override fun add(configurationName: String, dependencyNotation: Any): Dependency? {
        return this.add(configurationName, dependencyNotation, null)
    }

    override fun add(
        configurationName: String,
        dependencyNotation: Any,
        configureClosure: Closure<*>?
    ): Dependency? {
        val newNotation = when (dependencyNotation) {
            else -> dependencyNotation
        }

        val newConfig =
            ((if (sourceSet.name == SourceSet.MAIN_SOURCE_SET_NAME)
                ""
            else GUtil.toCamelCase(sourceSet.name)) + configurationName.replaceFirstChar {
                it.uppercase()
            }).replaceFirstChar { it.lowercase() }

        return delegate.add(newConfig, newNotation, configureClosure)?.also(addDependency)
    }
}

class VersionPartitionDependencyHandler(
    private val delegate: DependencyHandler,
    private val sourceSet: SourceSet,
    private val project: Project,
    private val mappingsType: String,
    addDependency: (Dependency) -> Unit
) : PartitionDependencyHandler(
    delegate,
    sourceSet,
    addDependency
) {
    fun minecraft(version: String) {
        val taskName = "generateMinecraft${version}Sources"
        val task = project.tasks.findByName(taskName) ?: project.tasks.create(taskName, GenerateMcSources::class.java) {
            it.mappingProvider.set(mappingsType)
            it.minecraftVersion.set(version)
        }

        delegate.add(sourceSet.implementationConfigurationName,
            project.files(task.outputs.files.asFileTree).apply {
                builtBy(task)
            }
        )
    }
}