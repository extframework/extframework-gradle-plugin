package net.yakclient.gradle

import groovy.lang.Closure
import net.yakclient.common.util.resolve
import net.yakclient.components.extloader.api.mapping.MappingsProvider
import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import java.nio.file.Path

internal const val CLIENT_VERSION = "1.1-SNAPSHOT"
internal const val CLIENT_MAIN_CLASS = "net.yakclient.client.MainKt"
internal val YAKCLIENT_DIR = Path.of(System.getProperty("user.home")) resolve ".yakclient"

class YakClientGradle : Plugin<Project> {
    override fun apply(project: Project) {

        project.plugins.apply(JvmEcosystemPlugin::class.java)
        val yakclient = project.extensions.create("yakclient", YakClientExtension::class.java, project)

        val generateErm = project.registerGenerateErmTask(yakclient)

        val jar = project.tasks.named("jar", Jar::class.java) { jar ->
            jar.dependsOn(generateErm)
            jar.from(generateErm)
            jar.dependsOn(project.tasks.withType(GenerateMcSources::class.java))

            yakclient.partitions.configureEach { partition ->
                jar.from(partition.sourceSet.output) { copy ->
                    copy.into("META-INF/versioning/partitions/${partition.name}")
                }
            }

            if (yakclient.tweakerPartition.isPresent) jar.from(yakclient.tweakerPartition.get().sourceSet.output) { copy ->
                copy.into("META-INF/versioning/partitions/tweaker")
            }
        }

//        val publishDevExtension = project.tasks.register("publishDevExtension", Copy::class.java) { copy ->
//            val basePath = yakclient.erm.map {
//                Path.of(
//                    it.groupId.replace('.', '/')
//                ).resolve(
//                    it.name
//                ).resolve(
//                    it.version
//                ).toString()
//            }
//            val ermOut = generateErm.get().outputs
//            ermOut.upToDateWhen { false }
//            copy.from(ermOut) { spec ->
//                spec.into(basePath)
//            }
//            val jarOut = jar.get().outputs
//            jarOut.upToDateWhen { false }
//            copy.from(jarOut) { spec ->
//                spec.into(basePath)
//            }
//            copy.destinationDir = project.mavenLocal().toFile()
//        }

        project.registerLaunchTask(yakclient, project.tasks.getByName("publishToMavenLocal"))
    }
}

const val MAIN_INCLUDE_CONFIGURATION_NAME = "include"
const val TWEAKER_INCLUDE_CONFIGURATION_NAME = "tweakerInclude"
const val MAIN_EXTENSION_CONFIGURATION_NAME = "extension"
const val TWEAKER_EXTENSION_CONFIGURATION_NAME = "tweakerExtension"



abstract class YakClientExtension(
    private val project: Project
) {
    internal val partitions = project.container(VersionPartition::class.java) { name ->
        check(name != "main" && name != "tweaker") { "Illegal partition name: '$name', this is a reserved partition name." }

        val sourceSet = sourceSets.findByName(name) ?: sourceSets.create(name)
        project.configurations.create("${name}Extension")

        VersionPartition(project, name, sourceSet, project.objects.property(String::class.java), this)
    }
    internal val sourceSets: SourceSetContainer = project.extensions.getByType(SourceSetContainer::class.java)
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
            project.objects.property(String::class.java),
            project.property {
                MutableMainVersionPartition(
                    project.property {
                        "main"
                    },
                    project.property {
                        ""
                    },
                    project.newSetProperty(),
                    project.newSetProperty(),
                )
            },
            project.newSetProperty(),
            project.newSetProperty(),
            project.newSetProperty(),
            project.property()
        )
    }
    val extensionConfiguration: Configuration = project.configurations.create("extension")
    val mappingProviders = project.container(MinecraftDeobfuscator::class.java)
    val tweakerPartition: Property<TweakerPartition> = project.property()

    init {
        mappingProviders.add(object: MinecraftDeobfuscator {
            override val provider: MappingsProvider = MojangMappingProvider()
            override val obfuscatedNamespace: String = MojangMappingProvider.OBF_NS
            override val deobfuscatedNamespace: String = MojangMappingProvider.DEOBF_NS
            override fun getName(): String {
                return "mojang"
            }
        })

        project.configurations.create(MAIN_INCLUDE_CONFIGURATION_NAME)
    }

    fun partitions(configure: Action<NamedDomainObjectContainer<VersionPartition>>) {
        configure.execute(partitions)
    }

    fun tweakerPartition(configure: Action<TweakerPartition>) {
        if (!tweakerPartition.isPresent) tweakerPartition.set(
            TweakerPartition(
                project,
                sourceSets.create("tweaker"),
                this
            )
        )

        configure.execute(tweakerPartition.get())
    }

    fun extension(notation: String) {
        val task =
            project.tasks.register("downloadExtension${notation.split(":")[1]}", DownloadExtensions::class.java) {
                it.notation.set(notation)
            }

        project.dependencies.add(
            "implementation",
            task.map {
                it.output.get()["main"]!!.asFileTree
            }
        )

        tweakerPartition.ifPresent { p ->
            p.dependencies.add(
                "implementation",
                task.map {
                    it.output.get()["tweaker"]!!.asFileTree
                }
            )
        }

        partitions.configureEach { p ->
            p.dependencies.add(
                "implementation",
                task.map { it.output.get()[p.name]!!.asFileTree }
            )
        }
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

            return mapOf( // Always a good idea to fill out default values even if they are provided, just in case libraries updates etc.
                "descriptor" to ("${dependency.group}:${dependency.name}:${dependency.version}"),
                "isTransitive" to "true",
                "includeScopes" to "compile,runtime,import",
                "excludeArtifacts" to ""
            )
        }
    }
}

data class TweakerPartition(
    private val project: Project,
    val sourceSet: SourceSet,
    private val yakclientExtension: YakClientExtension
) {
    val entrypoint: Property<String> = project.objects.property(String::class.java)
    val dependencies = PartitionDependencyHandler(
        project.dependencies, sourceSet, "tweaker", project.configurations.maybeCreate(
            TWEAKER_INCLUDE_CONFIGURATION_NAME
        ), yakclientExtension
    )

    fun dependencies(action: Action<PartitionDependencyHandler>) {
        action.execute(dependencies)
    }
}

data class VersionPartition(
    private val project: Project,
    private val name: String,
    val sourceSet: SourceSet,
    val mappingsType: Property<String>,
    private val yakClientExtension: YakClientExtension
) : Named {
    val dependencies = MinecraftEnabledPartitionDependencyHandler(
        project.dependencies,
        sourceSet,
        name,
        project,
        mappingsType,
        project.configurations.maybeCreate("${name}Include"),
        yakClientExtension,
    )
    val supportedVersions: SetProperty<String> = project.newSetProperty()

    fun dependencies(action: Action<MinecraftEnabledPartitionDependencyHandler>) =
        action.execute(dependencies)

    override fun getName(): String {
        return name
    }
}

open class PartitionDependencyHandler(
    private val delegate: DependencyHandler,
    val sourceSet: SourceSet,
    private val name: String,
    val includeConfiguration: Configuration,
    yakClientExtension: YakClientExtension,
) : DependencyHandler by delegate {
    val main by lazy { yakClientExtension.sourceSets.getByName("main").output }

    override fun add(configurationName: String, dependencyNotation: Any): Dependency? {
        return add(configurationName, dependencyNotation, null)
    }

    operator fun String.invoke(notation: Any) {
        add(this, notation)
    }

    override fun add(
        configurationName: String,
        dependencyNotation: Any,
        configureClosure: Closure<*>?
    ): Dependency? {
        val newNotation = when (dependencyNotation) {
            is VersionPartition -> dependencyNotation.sourceSet.output
            else -> dependencyNotation
        }

        val newConfig = when (configurationName) {
            "implementation" -> sourceSet.implementationConfigurationName
            else -> configurationName
        }

        return delegate.add(newConfig, newNotation, configureClosure)
    }
}

fun PartitionDependencyHandler.extensionInclude(dependencyNotation: Any) {
    add(sourceSet.implementationConfigurationName, dependencyNotation, null)
    add(includeConfiguration.name, dependencyNotation, null)
}

fun DependencyHandler.extensionInclude(dependencyNotation: Any) {
    assert(this !is PartitionDependencyHandler) { "Dependency handler cannot be ParittionDependencyHandler" }
    add(MAIN_INCLUDE_CONFIGURATION_NAME, dependencyNotation)
    add("implementation", dependencyNotation)
}


class MinecraftEnabledPartitionDependencyHandler(
    delegate: DependencyHandler, sourceSet: SourceSet, name: String,
    private val project: Project,
    private val mappingsType: Provider<String>,
    includeConfiguration: Configuration,
    yakClientExtension: YakClientExtension,
) : PartitionDependencyHandler(
    delegate,
    sourceSet,
    name,
    includeConfiguration,
    yakClientExtension,
) {
    var minecraftVersion: String = ""
        private set

    fun minecraft(version: String) {
        minecraftVersion = version

        val taskName = "generateMinecraft${version}Sources"
        val task =
            project.tasks.findByName(taskName) ?: project.tasks.create(taskName, GenerateMcSources::class.java) {
                it.mappingProvider.set(mappingsType)
                it.minecraftVersion.set(version)
            }

        add("implementation",
            project.files(task.outputs.files.asFileTree).apply {
                builtBy(task)
            }
        )
    }
}