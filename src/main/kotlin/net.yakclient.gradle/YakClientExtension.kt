package net.yakclient.gradle

import net.yakclient.components.extloader.extension.partition.MainPartitionLoader
import net.yakclient.components.extloader.extension.partition.TweakerPartitionLoader
import net.yakclient.components.extloader.extension.partition.VersionedPartitionLoader
import net.yakclient.gradle.deobf.MinecraftDeobfuscator
import net.yakclient.gradle.deobf.MinecraftMappings
import net.yakclient.gradle.fabric.tasks.registerFabricModTask
import net.yakclient.gradle.tasks.DownloadExtensions
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSetContainer

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

            eagerModel {
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

            eagerModel {
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

        override fun version(name: String, action: Action<VersionedPartitionHandler>) {
            val partition = MutableExtensionPartition(
                VersionedPartitionLoader.TYPE,
                project.property { name },
                project.property { "META-INF/partitions/$name" },
                project.newSetProperty(),
                project.newSetProperty(),
                project.newMapProperty()
            )

            eagerModel {
                it.partitions.add(partition)
            }

            val sourceSet = sourceSets.create(name)
            project.dependencies.add(sourceSet.implementationConfigurationName, sourceSets.getByName("main").output)
            project.dependencies.add(sourceSet.implementationConfigurationName, downloadExtensions.map {
                it.output
            })
            val toConfigure = ArrayList<() -> Unit>()
            val handler = VersionedPartitionHandler(
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

    private val extensionConfiguration: Configuration = project.configurations.create("extension") {
        it.isCanBeResolved = false
    }
    internal val downloadExtensions = project.tasks.register("downloadExtensions", DownloadExtensions::class.java) {
        it.configuration.set(extensionConfiguration.name)
    }

    private val fabricModConfiguration: Configuration = project.configurations.create("fabricMod") {
        it.isCanBeResolved = false
    }
    internal val downloadFabricMods = registerFabricModTask(this, project, fabricModConfiguration)

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

    val mappingProviders: NamedDomainObjectContainer<MinecraftDeobfuscator> = project.container(MinecraftDeobfuscator::class.java)

    init {
        mappingProviders.addAll(
            listOf(
                MinecraftMappings.mojang,
                MinecraftMappings.fabric
            )
        )
    }

    fun partitions(action: Action<NamedDomainPartitionContainer>) {
        action.execute(partitions)
    }

    fun extensions(action: Action<ExtensionDependencyHandler>) {
        action.execute(object : ExtensionDependencyHandler {
            override fun require(notation: String) {
                val dep = project.dependencies.add(extensionConfiguration.name, notation) ?: return

                eagerModel {
                    it.extensions.add(ermDependency(dep))
                }
            }

            override fun fabricMod(name: String, projectId: String, fileId: String) {
                require("net.yakclient.integrations:fabric-ext:1.0-SNAPSHOT")

                this@YakClientExtension.project.dependencies.add(fabricModConfiguration.name, "curse.maven:$name-$projectId:$fileId")
                model {
                    it.partitions.get()
                        .filter { it.type == MainPartitionLoader.TYPE || it.type == VersionedPartitionLoader.TYPE }
                        .forEach { p ->
                            p.dependencies.add(
                                mutableMapOf(
                                    "name" to name,
                                    "projectId" to projectId,
                                    "fileId" to fileId
                                )
                            )
                        }
                }
            }
        })
    }

    fun model(action: Action<MutableExtensionRuntimeModel>) {
        project.afterEvaluate {
            eagerModel(action)
        }
    }

    internal fun eagerModel(action: Action<MutableExtensionRuntimeModel>) {
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

    fun fabricMod(
        name: String,
        projectId: String,
        fileId: String
    )
}

abstract class NamedDomainPartitionContainer(
    delegate: NamedDomainObjectContainer<PartitionHandler<*>>
) : NamedDomainObjectContainer<PartitionHandler<*>> by delegate {
    abstract fun main(action: Action<MainPartitionHandler>)

    abstract fun tweaker(action: Action<TweakerPartitionHandler>)

    abstract fun version(name: String, action: Action<VersionedPartitionHandler>)
}
