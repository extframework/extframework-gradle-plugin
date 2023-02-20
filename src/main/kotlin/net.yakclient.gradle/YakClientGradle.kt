package net.yakclient.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.jvm.tasks.Jar
import kotlin.reflect.KProperty

class YakClientGradle : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(JvmEcosystemPlugin::class.java)
        val yakclient = project.extensions.create("yakclient", YakClient::class.java, project)

        project.dependencies.add("implementation", "net.yakclient:client-api:1.0-SNAPSHOT")

        val generateErm = project.tasks.register("generateErm", GenerateErm::class.java)


        project.tasks.register("extJar", Jar::class.java) { jar ->
            jar.dependsOn(generateErm)
            jar.from(generateErm)

            val outputs = yakclient.versionPartitionHandler.partitions

            outputs.forEach { partition ->
                println("Partitions are: " + partition.sourceSet.output.map { it.name })
                jar.from(partition.sourceSet.output) { copy ->
                    copy.into("META-INF/versioning/partitions/${partition.name}")
                }
            }
        }
    }
}

open class YakClient(
    private val project: Project
) {
    internal val versionPartitionHandler = VersionPartitionHandler()
    private val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
    val erm: ExtensionRuntimeModel = ExtensionRuntimeModel(
        project.group.toString(),
        project.name,
        project.version.toString(),
        versioningPartitions = HashMap()
    )


    fun partitions(configure: Action<VersionPartitionHandler>) {
        configure.execute(versionPartitionHandler)
    }

    fun model(action: Action<ExtensionRuntimeModel>) {
        action.execute(erm)
    }

    fun jar(action: Action<Jar>) {
        action.execute(project.tasks.withType(Jar::class.java).first { it.name == "extJar" })
    }

    inner class VersionPartitionHandler internal constructor() {
        internal val partitions: MutableList<VersionPartition> = ArrayList()

        fun create(name: String, configurer: Action<VersionPartition>) {
            println("Creating: '$name'")
            val sourceSet = sourceSets.create(name)

            val versionPartition = VersionPartition(
                project,
                name,
                sourceSet,
                ArrayList(),
            )

            partitions.add(versionPartition)
            configurer.execute(versionPartition)
        }
    }


    data class VersionPartition(
        private val project: Project,
        val name: String,
        val sourceSet: SourceSet,
        val supportedVersions: MutableList<String>,
        val excluded: Boolean = false,
    ) {
        internal val dependencyScope = DependencyScope()

        fun dependencies(action: Action<DependencyScope>) = action.execute(dependencyScope)

        inner class DependencyScope {
            internal val minecraftDependencies = ArrayList<String>()

            fun other(name: String): SourceSetOutput {
                return project.extensions.getByType(YakClient::class.java).sourceSets.getByName(name).output
            }

            fun other(): GettingDelegate<SourceSetOutput> = GettingDelegate(::other)

            fun implementation(notation: Any) {
                project.dependencies.add(sourceSet.implementationConfigurationName, notation)
            }

            fun minecraft(version: String) {
                minecraftDependencies.add(version)
                println("Adding version: '$version'")

                val task = project.tasks.register("generateMinecraft${version}Sources", GenerateMcSources::class.java) {
                    it.minecraftVersion.set(version)
                }

                project.dependencies.add(
                    sourceSet.implementationConfigurationName,
                    project.files(task.get().outputs.files).apply {
                        builtBy(task)
                    },
                )
            }
        }

    }

    data class MinecraftVersion(
        val version: String = "<NOT SET>",
        val mappings: String = "",
    )
}

class GettingDelegate<out T>(
    private val provider: (String) -> T
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return provider(property.name)
    }
}

interface AnnotationProcessorConnection {
    fun poll(): Set<ExtensionMixin>
}