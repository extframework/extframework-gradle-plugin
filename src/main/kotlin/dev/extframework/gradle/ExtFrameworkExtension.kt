package dev.extframework.gradle

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import dev.extframework.gradle.deobf.MinecraftDeobfuscator
import dev.extframework.gradle.deobf.MinecraftMappings
import dev.extframework.gradle.fabric.tasks.registerFabricModTask
import dev.extframework.gradle.tasks.DownloadExtensions
import dev.extframework.gradle.tasks.GenerateMcSources
import dev.extframework.tooling.api.TOOLING_API_VERSION
import dev.extframework.tooling.api.extension.ExtensionParent
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar

abstract class ExtFrameworkExtension(
    internal val project: Project
) {
    internal val partitions = object : NamedDomainPartitionContainer(project.container(PartitionHandler::class.java)) {
        private fun <T : PartitionHandler<*>> doAdd(action: Action<T>, getHandler: ((() -> Unit) -> Unit) -> T): T {
            val toConfigure = ArrayList<() -> Unit>()

            val handler = getHandler(toConfigure::add)


            add(handler)
            eagerModel {
                it.partitions.add(
                    handler.partition
                )
            }

            project.tasks.maybeCreate(
                handler.sourceSet.jarTaskName,
                Jar::class.java
            )
            project.tasks
                .withType(Jar::class.java)
                .named(handler.sourceSet.jarTaskName).configure {
                    it.from(handler.sourceSet.output)
                    it.dependsOn(project.tasks.withType(GenerateMcSources::class.java))
                    it.archiveClassifier.set(handler.name)
                }

//            project.tasks.register(
//                handler.generatePrmTaskName,
//                GeneratePrm::class.java
//            ) {
//                it.partitionName.set(handler.name)
//            }

            action.execute(handler)
            toConfigure.forEach { it() }

            return handler
        }

        override fun main(action: Action<MainPartitionHandler>) {
            val partition = MutablePartitionRuntimeModel(
                "main",
                "main",
                project.newListProperty(),
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
            val partition = MutablePartitionRuntimeModel(
                "tweaker",
                "tweaker",
                project.newListProperty(),
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

        override fun version(name: String, action: Action<MinecraftPartitionHandler>) {
            val partition = MutablePartitionRuntimeModel(
                "minecraft",
                name,
                project.newListProperty(),
                project.newSetProperty(),
                project.newMapProperty()
            )

            val sourceSet = sourceSets.create(name)
            project.dependencies.add(
                sourceSet.implementationConfigurationName,
                sourceSets.getByName("main").output
            )

            val handler = doAdd(action) {
                MinecraftPartitionHandler(
                    project,
                    partition,
                    sourceSet,
                    this@ExtFrameworkExtension,
                    it
                )
            }

            if (handler.requiresFabric) {
                val taskDir = project.projectDir.resolve("build-ext").resolve("fabric").resolve(name)

                val task = registerFabricModTask(
                    project,
                    partition.name,
                    handler.mappings.deobfuscatedNamespace,
                    handler.supportedVersions.firstOrNull()
                        ?: throw IllegalArgumentException("Please set at least 1 supported version via the #supportVersions(version: String) method."),
                    taskDir.toPath()
                )

                project.dependencies.add(
                    sourceSet.implementationConfigurationName,
                    project.fileTree(taskDir).builtBy(task)
                )
            }
        }
    }

    internal val sourceSets: SourceSetContainer = project.extensions.getByType(SourceSetContainer::class.java)

    internal val downloadExtensions: TaskProvider<DownloadExtensions> = project.tasks.register("downloadExtensions", DownloadExtensions::class.java)

    val erm: Property<MutableExtensionRuntimeModel> = project.property {
        MutableExtensionRuntimeModel(
            TOOLING_API_VERSION,
            project.provider {
                project.group as? String ?: throw Exception("No 'project.group' set!")
            },
            project.property {
                project.name
            },
            project.provider {
                project.version as? String ?: throw Exception("No 'project.version' set!")
            },
            project.newListProperty(),
            project.newSetProperty(),
            project.newSetProperty(),
        )
    }
    private val ermUpdates = ArrayList<Action<MutableExtensionRuntimeModel>>()

    val metadata: Property<MutableExtensionMetadata> = project.property {
        MutableExtensionMetadata(
            project.property(),
            project.newListProperty(),
            project.property(),
            project.property(),
            project.newListProperty()
        )
    }

    private val finalizationListeners = ArrayList<Action<ExtFrameworkExtension>>()

    val mappingProviders: NamedDomainObjectContainer<MinecraftDeobfuscator> =
        project.container(MinecraftDeobfuscator::class.java)

    init {
        mappingProviders.addAll(
            listOf(
                MinecraftMappings.mojang,
                MinecraftMappings.fabric,
                MinecraftMappings.none,
                MinecraftMappings.mcpLegacy
            )
        )

        extensions {
            it.require("dev.extframework.core:minecraft:$CORE_MC_VERSION")
        }

        project.afterEvaluate {
            ermUpdates.forEach {
                eagerModel(it)
            }
            finalizationListeners.forEach {
                it.execute(this)
            }
        }
    }

    fun partitions(action: Action<NamedDomainPartitionContainer>) {
        action.execute(partitions)
    }

    fun extensions(): ExtensionDependencyHandler {
        return object : ExtensionDependencyHandler {
            override fun require(notation: String) {
                eagerModel {
                    val descriptor = SimpleMavenDescriptor.parseDescription(notation)
                        ?: throw IllegalArgumentException("Invalid notation: '$notation'. Group, artifact, and version id's must all be present and non-blank!")

                    it.parents.add(
                        ExtensionParent(descriptor.group, descriptor.artifact, descriptor.version)
                    )
                }

                downloadExtensions.configure {
                    it.dependencies.add(notation)
                }
            }
        }
    }

    fun extensions(action: Action<ExtensionDependencyHandler>) {
        action.execute(extensions())
    }

    fun model(action: Action<MutableExtensionRuntimeModel>) {
        ermUpdates.add(action)
    }

    internal fun eagerModel(action: Action<MutableExtensionRuntimeModel>) {
        erm.update {
            it.map { erm ->
                action.execute(erm)
                erm
            }
        }
    }

    fun metadata(action: Action<MutableExtensionMetadata>) {
        project.afterEvaluate {
            eagerMetadata(action)
        }
    }

    internal fun eagerMetadata(action: Action<MutableExtensionMetadata>) {
        metadata.update {
            it.map { metadata ->
                action.execute(metadata)
                metadata
            }
        }
    }

    internal fun afterFinalized(action: Action<ExtFrameworkExtension>) {
        finalizationListeners.add(action)
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

//    fun fabricMod(
//        name: String,
//        projectId: String,
//        fileId: String
//    )
}

abstract class NamedDomainPartitionContainer(
    delegate: NamedDomainObjectContainer<PartitionHandler<*>>
) : NamedDomainObjectContainer<PartitionHandler<*>> by delegate {
    abstract fun main(action: Action<MainPartitionHandler>)

    abstract fun tweaker(action: Action<TweakerPartitionHandler>)

    abstract fun version(name: String, action: Action<MinecraftPartitionHandler>)
}
