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

        project.tasks.named("jar", Jar::class.java) { jar ->
            jar.dependsOn(generateErm)
            jar.from(generateErm)

            yakclient.partitions.configureEach { partition ->
                jar.from(partition.sourceSet.output) { copy ->
                    copy.into("META-INF/versioning/partitions/${partition.name}")
                }
            }
        }

        project.registerLaunchTask(yakclient)
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
    val sourceSets: SourceSetContainer = project.extensions.getByType(SourceSetContainer::class.java)
    var erm: ExtensionRuntimeModel = ExtensionRuntimeModel(
        "",
        "",
        "", versionPartitions = ArrayList()
    )

//    > = project.provider {
//        ExtensionRuntimeModel(
//            project.group as String,
//            project.name,
//            project.version as String,
//            versionPartitions = ArrayList()
//        )
//    }



    fun partitions(configure: Action<NamedDomainObjectContainer<VersionPartition>>) {
        configure.execute(partitions)
    }

    fun model(action: Action<ExtensionRuntimeModel>) {
        action.execute(erm)
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

            val task = project.tasks.register("generateMinecraft${version}Sources", GenerateMcSources::class.java) {
                it.minecraftVersion.set(version)
            }

            add(
                "implementation",
                project.files(task.get().outputs.files).apply {
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
//class GettingDelegate<out T>(
//        private val provider: (String) -> T
//) {
//    private var cache: T? = null
//
//    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
//        return cache ?: provider(property.name).also { cache = it }
//    }
//}