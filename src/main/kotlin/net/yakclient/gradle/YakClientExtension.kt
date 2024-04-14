package net.yakclient.gradle

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import net.yakclient.components.extloader.extension.partition.MainPartitionLoader
import net.yakclient.components.extloader.extension.partition.TweakerPartitionLoader
import net.yakclient.components.extloader.extension.partition.VersionedPartitionLoader
import net.yakclient.gradle.deobf.MinecraftDeobfuscator
import net.yakclient.gradle.deobf.MinecraftMappings
import net.yakclient.gradle.fabric.tasks.DownloadFabricMod
import net.yakclient.gradle.fabric.tasks.registerFabricModTask
import net.yakclient.gradle.tasks.DownloadExtensions
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSetContainer
import java.lang.IllegalArgumentException

abstract class YakClientExtension(
    internal val project: Project
) {
    internal val partitions = object : NamedDomainPartitionContainer(project.container(PartitionHandler::class.java)) {
        private fun <T : PartitionHandler<*>> doAdd(action: Action<T>, getHandler: ((() -> Unit) -> Unit) -> T): T {
            val toConfigure = ArrayList<() -> Unit>()

            val handler = getHandler(toConfigure::add)

            add(handler)
            eagerModel {
                it.partitions.add(handler.partition)
            }

            action.execute(handler)
            toConfigure.forEach { it() }

            return handler
        }

        override fun main(action: Action<MainPartitionHandler>) {
            val partition = MutableExtensionPartition(
                MainPartitionLoader.TYPE,
                project.property { "main" },
                project.property { "META-INF/partitions/main" },
                project.newSetProperty(),
                project.newSetProperty(),
                project.newMapProperty()
            )

            doAdd(action) {
                MainPartitionHandler(
                    project,
                    partition,
                    sourceSets.getByName("main"),
                    it
                )
            }
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

            doAdd(action) {
                val sourceSet = sourceSets.create("tweaker")
                project.dependencies.add("implementation", sourceSet.output)

                TweakerPartitionHandler(
                    project,
                    partition,
                    sourceSet,
                    it
                )
            }
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

            val sourceSet = sourceSets.create(name)
            project.dependencies.add(sourceSet.implementationConfigurationName, sourceSets.getByName("main").output)
            project.dependencies.add(sourceSet.implementationConfigurationName, downloadExtensions.map {
                it.output
            })

            val handler = doAdd(action) {
                VersionedPartitionHandler(
                    project,
                    partition,
                    sourceSet,
                    this@YakClientExtension,
                    it
                )
            }

            val task = registerFabricModTask(
                project,
                partition,
                handler.mappings.deobfuscatedNamespace,
                handler.supportedVersions.first(),
                project.projectDir.resolve("build-ext").resolve("fabric").toPath()
            )

            project.dependencies.add(
                sourceSet.implementationConfigurationName,
                project.fileTree(project.projectDir.resolve("build-ext").resolve("fabric")).builtBy(task)
            )
        }
    }
    internal val sourceSets: SourceSetContainer = project.extensions.getByType(SourceSetContainer::class.java)

    internal val downloadExtensions = project.tasks.register("downloadExtensions", DownloadExtensions::class.java)

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

    val mappingProviders: NamedDomainObjectContainer<MinecraftDeobfuscator> =
        project.container(MinecraftDeobfuscator::class.java)


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
                eagerModel {
                    it.extensions.add(ermDependency(notation) ?: throw IllegalArgumentException("Invalid notation: '$notation'. Group, artifact, and version id's must all be present and non-blank!"))
                }

                downloadExtensions.configure {
                    it.dependencies.add(notation)
                }
            }

            override fun fabricMod(
                name: String,
                projectId: String,
                fileId: String,
            ) {
                require("net.yakclient.integrations:fabric-ext:1.0-SNAPSHOT")

                project.tasks.withType(DownloadFabricMod::class.java).configureEach {
                    it.mods.add("curse.maven:$name-$projectId:$fileId")
                }

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
            return ermDependency(
                "${dependency.group}:${dependency.name}:${dependency.version}"
            )
        }

        fun ermDependency(notation: String): Map<String, String>? {
            val dependency = SimpleMavenDescriptor.parseDescription(notation) ?: return null

            if (dependency.group.isBlank() ||
                dependency.artifact == "unspecified" ||
                dependency.version.isBlank()
            ) return null

            return mapOf( // Always a good idea to fill out default values even if they are provided just in case libraries update.
                "descriptor" to ("${dependency.group}:${dependency.artifact}:${dependency.version}"),
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
