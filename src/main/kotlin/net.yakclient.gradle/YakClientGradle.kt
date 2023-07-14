package net.yakclient.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import java.nio.file.Path
import kotlin.reflect.KProperty

fun Project.mavenLocal() : Path = Path.of(repositories.mavenLocal().url)

class YakClientGradle : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(JvmEcosystemPlugin::class.java)
        val yakclient = project.extensions.create("yakclient", YakClientExtension::class.java, project)

        project.dependencies.add("implementation", "net.yakclient:client-api:1.0-SNAPSHOT")

        val generateErm = project.registerGenerateErmTask(yakclient)

        project.tasks.named("jar", Jar::class.java) { jar ->
            jar.dependsOn(generateErm)
            jar.from(generateErm)

            val outputs = yakclient.versionPartitionHandler.partitions

            outputs.forEach { partition ->
                jar.from(partition.sourceSet.output) { copy ->
                    copy.into("META-INF/versioning/partitions/${partition.name}")
                }
            }
        }

        project.registerLaunchTask(yakclient)
    }
}

open class YakClientExtension(
        private val project: Project
) {
    internal val versionPartitionHandler = VersionPartitionHandler()
    internal val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
    val erm: ExtensionRuntimeModel = ExtensionRuntimeModel(
            "",
            "",
            "",

            versionPartitions = ArrayList()
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
        lateinit var main: VersionPartition

        // BE CAREFUL! Will only evaluate if you access the delegate property!
        fun named(
                configuration: Action<VersionPartition>
        ): GettingDelegate<VersionPartition> = GettingDelegate {
            named(it, configuration)
        }


        fun named(name: String, configuration: Action<VersionPartition>): VersionPartition {
            val sourceSet = sourceSets.findByName(name) ?: sourceSets.create(name)

            val versionPartition = VersionPartition(
                    project,
                    name,
                    sourceSet,
                    ArrayList(),
                    false,
                    "${name}Extension"
            )

            project.configurations.create(versionPartition.extensionConfigurationName) {
                it.extendsFrom(project.configurations.named(sourceSet.implementationConfigurationName).get())
            }

            partitions.add(versionPartition)
            configuration.execute(versionPartition)

            return versionPartition
        }
    }


    data class VersionPartition(
            private val project: Project,
            val name: String,
            val sourceSet: SourceSet,
            val supportedVersions: MutableList<String>,
            val excluded: Boolean = false,
            val extensionConfigurationName: String
    ) {
        internal val dependencyScope = DependencyScope()

        fun dependencies(action: Action<DependencyScope>) = action.execute(dependencyScope)

        inner class DependencyScope {
            internal val minecraftDependencies = ArrayList<String>()

            operator fun String.invoke(notation: Any) {
                val newNotation = if (notation is VersionPartition) notation.sourceSet.output else notation

                project.dependencies.add(this, newNotation)
            }

            fun implementation(notation: Any) {
                sourceSet.implementationConfigurationName(notation)
            }

            fun minecraft(version: String) {
                minecraftDependencies.add(version)

                val task = project.tasks.register("generateMinecraft${version}Sources", GenerateMcSources::class.java) {
                    it.minecraftVersion.set(version)
                }

                implementation(
                        project.files(task.get().outputs.files).apply {
                            builtBy(task)
                        }
                )
            }

            fun extension(notation: Any) {
                extensionConfigurationName(notation)
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

class GettingDelegate<out T>(
        private val provider: (String) -> T
) {
    private var cache: T? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return cache ?: provider(property.name).also { cache = it }
    }
}