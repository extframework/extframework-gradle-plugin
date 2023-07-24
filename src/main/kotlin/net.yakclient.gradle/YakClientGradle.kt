package net.yakclient.gradle

import org.gradle.api.Action
import groovy.lang.Closure
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Transformer
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
import kotlin.reflect.KProperty

fun Project.mavenLocal(): Path = Path.of(repositories.mavenLocal().url)

class YakClientGradle : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(JvmEcosystemPlugin::class.java)
        val yakclient = project.extensions.create("yakclient", YakClientExtension::class.java, project)

        project.dependencies.add("implementation", "net.yakclient:client-api:1.0-SNAPSHOT")

        val generateErm = project.registerGenerateErmTask(yakclient)

        val jar = project.tasks.named("jar", Jar::class.java) { jar ->
            jar.dependsOn(generateErm)
            jar.from(generateErm)

            yakclient.partitions.configureEach { partition ->
                jar.from(partition.sourceSet.output) { copy ->
                    copy.into("META-INF/versioning/partitions/${partition.name}")
                }
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

abstract class YakClientExtension(
    private val project: Project
) {
    internal val partitions = project.container(VersionPartition::class.java) { name ->
        val sourceSet = sourceSets.findByName(name) ?: sourceSets.create(name)
        project.configurations.create("${name}Extension") { c ->
            c.extendsFrom(project.configurations.named(sourceSet.implementationConfigurationName).get())
        }
        VersionPartition(project, name, sourceSet)
    }
    internal val sourceSets: SourceSetContainer = project.extensions.getByType(SourceSetContainer::class.java)
    val erm: Property<ExtensionRuntimeModel> = project.objects.property(ExtensionRuntimeModel::class.java)
//    = ExtensionRuntimeModel(
//        "",
//        "",
//        "", versionPartitions = ArrayList()
//    )
//
//    > = project.provider {
//        ExtensionRuntimeModel(
//            project.group as String,
//            project.name,
//            project.version as String,
//            versionPartitions = ArrayList()
//        )
//    }

    init {
        project.afterEvaluate {
            if (!erm.isPresent) {
                erm.set(
                    ExtensionRuntimeModel(
                        project.group as String,
                        project.name,
                        project.version as String,
                        versionPartitions = ArrayList()
                    )
                )
            }
        }
    }


    fun partitions(configure: Action<NamedDomainObjectContainer<VersionPartition>>) {
        configure.execute(partitions)
    }

    fun model(action: Action<ExtensionRuntimeModel>) {
        val erm = ExtensionRuntimeModel(
            project.group as? String ?: "",
            project.name,
            project.version as? String ?: "",
            versionPartitions = ArrayList()
        )
        action.execute(erm)
        this.erm.set(erm)
//        erm = erm.map {
//            action.execute(it)
//            it
//        }
    }


//    fun jar(action: Action<Jar>) {
//        action.execute(project.tasks.withType(Jar::class.java).first { it.name == "extJar" })
//    }

//    inner class VersionPartitionHandler internal constructor() {
//        internal val partitions: MutableList<VersionPartition> = ArrayList()
//        lateinit var main: VersionPartition
//
//        // BE CAREFUL! Will only evaluate if you access the delegate property!
//        fun named(
//                configuration: Action<VersionPartition>
//        ): GettingDelegate<VersionPartition> = GettingDelegate {
//            named(it, configuration)
//        }
//
//
//        fun named(name: String, configuration: Action<VersionPartition>): VersionPartition {
//            val sourceSet = sourceSets.findByName(name) ?: sourceSets.create(name)
//
//            val versionPartition = VersionPartition(
//                    project,
//                    name,
//                    sourceSet,
//                    ArrayList(),
//                    false,
//                    "${name}Extension"
//            )
//
//            project.configurations.create(versionPartition.extensionConfigurationName) {
//                it.extendsFrom(project.configurations.named(sourceSet.implementationConfigurationName).get())
//            }
//
//            partitions.add(versionPartition)
//            configuration.execute(versionPartition)
//
//            return versionPartition
//        }
//    }


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

data class VersionPartition(
    private val project: Project,
    private val name: String,
    val sourceSet: SourceSet,
) : Named {
    var minecraftVersion: String? = null
    val supportedVersions: MutableList<String> = mutableListOf()
    var isMain: Boolean = name == "main"

    fun dependencies(action: Action<PartitionDependencyHandler>) =
        action.execute(PartitionDependencyHandler(project.dependencies))


    inner class PartitionDependencyHandler(
        private val delegate: DependencyHandler
    ) : DependencyHandler by delegate {
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

        fun minecraft(version: String) {
            minecraftVersion = version

            val taskName = "generateMinecraft${version}Sources"
            val task =
                project.tasks.findByName(taskName) ?: project.tasks.create(taskName, GenerateMcSources::class.java) {
                    it.minecraftVersion.set(version)
                }

            add(
                "implementation",
                project.files(task.outputs.files.asFileTree).apply {
                    builtBy(task)
                }
            )
        }

        fun extension(notation: Any) {
            add("extension", notation)
        }
    }

    override fun getName(): String {
        return name
    }
}