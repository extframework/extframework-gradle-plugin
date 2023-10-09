package net.yakclient.gradle

import org.gradle.api.Action
import groovy.lang.Closure
import net.yakclient.internal.api.mapping.MappingsProvider
import net.yakclient.`object`.ObjectContainerImpl
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import java.nio.file.Path

fun Project.mavenLocal(): Path = Path.of(repositories.mavenLocal().url)

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

            if (yakclient.sourceSets.findByName("tweaker") != null)
                jar.from(yakclient.tweakerPartition.sourceSet.output) { copy ->
                    copy.into("META-INF/versioning/partitions/tweaker")
                }
        }

        val publishDevExtension = project.tasks.register("publishDevExtension", Copy::class.java) { copy ->
            val basePath = yakclient.erm.map {
                Path.of(
                    it.groupId.replace('.', '/')
                ).resolve(
                    it.name
                ).resolve(
                    it.version
                ).toString()
            }
            val ermOut = generateErm.get().outputs
            ermOut.upToDateWhen { false }
            copy.from(ermOut) { spec ->
                spec.into(basePath)
            }
            val jarOut = jar.get().outputs
            jarOut.upToDateWhen { false }
            copy.from(jarOut) { spec ->
                spec.into(basePath)
            }
            copy.destinationDir = project.mavenLocal().toFile()
        }

        project.registerLaunchTask(yakclient, publishDevExtension)
    }
}

public val MAIN_INCLUDE_CONFIGURATION_NAME = "include"
public val TWEAKER_INCLUDE_CONFIGURATION_NAME = "tweakerInclude"

abstract class YakClientExtension(
    private val project: Project
) {
    internal val partitions = project.container(VersionPartition::class.java) { name ->
        check(name != "main" && name != "tweaker") { "Illegal partition name: '$name', this is a reserved partition name." }

        val sourceSet = sourceSets.findByName(name) ?: sourceSets.create(name)
        project.configurations.create("${name}Extension") { c ->
            c.extendsFrom(project.configurations.named(sourceSet.implementationConfigurationName).get())
        }
        VersionPartition(project, name, sourceSet, mappingType, this)
    }
    internal val sourceSets: SourceSetContainer = project.extensions.getByType(SourceSetContainer::class.java)
    var erm: Provider<ExtensionRuntimeModel> = project.provider {
        ExtensionRuntimeModel(
            project.group as String,
            project.name,
            project.version as String,
            mappingType = MojangMappingProvider.REAL_TYPE
        )
    }
    val mappingProviders = ObjectContainerImpl<MappingsProvider>()
    val tweakerPartition by lazy { TweakerPartition(project, sourceSets.create("tweaker"), this) }

    init {
        mappingProviders.register(
            MojangMappingProvider.REAL_TYPE,
            MojangMappingProvider()
        )

        project.configurations.create(MAIN_INCLUDE_CONFIGURATION_NAME)
    }

    var mappingType: String
        get() = erm.get().mappingType
        set(value) = model {
            it.mappingType = value
        }

    fun partitions(configure: Action<NamedDomainObjectContainer<VersionPartition>>) {
        configure.execute(partitions)
    }

    fun tweakerPartition(configure: Action<TweakerPartition>) {
        configure.execute(tweakerPartition)
    }

    fun model(action: Action<ExtensionRuntimeModel>) {
        this.erm = erm.map {
            action.execute(it)
            it
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
    private val yakClientExtension: YakClientExtension
) {
    val entrypoint: Property<String> = project.objects.property(String::class.java)
    val dependencyHandler = PartitionDependencyHandler(
        project.dependencies, sourceSet, "tweaker", project.configurations.create(
            TWEAKER_INCLUDE_CONFIGURATION_NAME
        ), yakClientExtension
    )

    init {
        yakClientExtension.partitions.configureEach {
            it.dependencyHandler.add("compileOnly", sourceSet.output)
        }
    }

    fun dependencies(action: Action<PartitionDependencyHandler>) {
        action.execute(dependencyHandler)
    }
}

data class VersionPartition(
    private val project: Project,
    private val name: String,
    val sourceSet: SourceSet,
    private val mappingsType: String,
   private val yakClientExtension: YakClientExtension
) : Named {
    val dependencyHandler = MinecraftEnabledPartitionDependencyHandler(
        project.dependencies, sourceSet, name, project, mappingsType, project.configurations.create("${name}Include"), yakClientExtension
    )
    val supportedVersions: MutableList<String> = mutableListOf()

    fun dependencies(action: Action<MinecraftEnabledPartitionDependencyHandler>) =
        action.execute(dependencyHandler)

    override fun getName(): String {
        return name
    }
}

open class PartitionDependencyHandler(
    private val delegate: DependencyHandler,
    private val sourceSet: SourceSet,
    private val name: String,
    val includeConfiguration: Configuration,
    yakClientExtension: YakClientExtension
) : DependencyHandler by delegate {
    val main = yakClientExtension.sourceSets.getByName("main").output

    override fun add(configurationName: String, dependencyNotation: Any): Dependency {
        return add(configurationName, dependencyNotation, null)
    }

    operator fun String.invoke(notation: Any) {
        add(this, notation)
    }

    override fun add(
        configurationName: String,
        dependencyNotation: Any,
        configureClosure: Closure<*>?
    ): Dependency {
        val newNotation = when (dependencyNotation) {
            is VersionPartition -> dependencyNotation.sourceSet.output
            else -> dependencyNotation
        }

        val newConfig = when (configurationName) {
            "implementation" -> sourceSet.implementationConfigurationName
            "extension" -> "${name}Extension"
            else -> configurationName
        }

        return delegate.add(newConfig, newNotation, configureClosure)
    }

    fun extension(notation: Any) {
        add("extension", notation)
    }
}

fun PartitionDependencyHandler.extensionInclude(dependencyNotation: Any) {
    add(includeConfiguration.name, dependencyNotation, null)
}

class MinecraftEnabledPartitionDependencyHandler(
    delegate: DependencyHandler, sourceSet: SourceSet, name: String,
    private val project: Project,
    private val mappingsType: String,
    includeConfiguration: Configuration,
    yakClientExtension: YakClientExtension
) : PartitionDependencyHandler(delegate, sourceSet, name, includeConfiguration, yakClientExtension) {
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