package dev.extframework.gradle

import groovy.lang.Closure
import dev.extframework.gradle.deobf.MinecraftDeobfuscator
import dev.extframework.gradle.tasks.GenerateMcSources
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.tasks.SourceSet
import org.gradle.util.internal.GUtil


abstract class PartitionHandler<T : PartitionDependencyHandler>(
    project: Project,
    val partition: MutablePartitionRuntimeModel,
    val sourceSet: SourceSet,
    // A shorthand for executing configurations just as the configuration block of this partition ends.
    private val configure: (() -> Unit) -> Unit,

) : Named {
    val generatePrmTaskName: String = "generatePrm${sourceSet.name.replaceFirstChar { it.uppercase() }}"
    protected val extframework: ExtFrameworkExtension = project.extensions.getByType(ExtFrameworkExtension::class.java)

    abstract val dependencies: T

    init {
        project.dependencies.add(sourceSet.implementationConfigurationName, extframework.downloadExtensions.map {
            it.output.asFileTree
        })
    }

    open fun model(action: Action<MutablePartitionRuntimeModel>) {
        action.execute(partition)
    }

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
    partition: MutablePartitionRuntimeModel,
    sourceSet: SourceSet, configure: (() -> Unit) -> Unit
) : PartitionHandler<PartitionDependencyHandler>(project, partition, sourceSet, configure) {
    override val dependencies = PartitionDependencyHandler(
        project.dependencies, sourceSet
    ) {
        ExtFrameworkExtension.ermDependency(it)
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
    partition: MutablePartitionRuntimeModel,
    sourceSet: SourceSet, configure: (() -> Unit) -> Unit
) : PartitionHandler<PartitionDependencyHandler>(project, partition, sourceSet, configure) {
    override val dependencies = PartitionDependencyHandler(
        project.dependencies, sourceSet
    ) {
        ExtFrameworkExtension.ermDependency(it)
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

class VersionedPartitionHandler(
    project: Project,
    partition: MutablePartitionRuntimeModel,
    sourceSet: SourceSet,
    val extFrameworkExtension: ExtFrameworkExtension, configure: (() -> Unit) -> Unit,
) : PartitionHandler<VersionPartitionDependencyHandler>(project, partition, sourceSet, configure) {
    override val dependencies: VersionPartitionDependencyHandler by lazy {
        VersionPartitionDependencyHandler(
            project.dependencies,
            sourceSet,
            project,
            mappings.name,
        ) {
            ExtFrameworkExtension.ermDependency(it)
                ?.toMutableMap()
                ?.let(partition.dependencies::add)
        }
    }

    var mappings: MinecraftDeobfuscator
        get() {
            return extFrameworkExtension.mappingProviders.find {
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
    protected val delegate: DependencyHandler,
    val sourceSet: SourceSet,
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
    delegate: DependencyHandler,
    sourceSet: SourceSet,
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