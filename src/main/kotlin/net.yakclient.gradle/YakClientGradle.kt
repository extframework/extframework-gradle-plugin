package net.yakclient.gradle

import net.yakclient.gradle.YakClientExtension.Companion.ermDependency
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import kotlin.reflect.KProperty

class YakClientGradle : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(JvmEcosystemPlugin::class.java)
        val yakclient = project.extensions.create("yakclient", YakClientExtension::class.java, project)

        val logger = project.logger

        project.dependencies.add("implementation", "net.yakclient:client-api:1.0-SNAPSHOT")

        val configureYak = project.tasks.register("configureYakclient") {
            yakclient.erm.groupId = it.project.group.toString()
            yakclient.erm.name = it.project.name
            yakclient.erm.version = it.project.version.toString()
        }

        val generateErm = project.tasks.register("generateErm", GenerateErm::class.java) { task ->
            task.dependsOn(configureYak)
            val sourceSets = yakclient.sourceSets

            sourceSets.forEach {
                task.dependsOn(project.tasks.named(it.classesTaskName).get())
            }

            task.preprocessorOutput.set(
                sourceSets
                    .associateBy { it.name }
                    .mapValues { project.files(it.value.output.files) }
            )

            task.doFirst(object : Action<Task> {
                override fun execute(t: Task) {
                    val project = t.project
                    yakclient.erm.groupId = project.group.toString()
                    yakclient.erm.name = project.name
                    yakclient.erm.version = project.version.toString()

                    val extensionRepositories: List<ExtensionRepository> = project.repositories
                        .onEach {
                            if (it !is MavenArtifactRepository) logger.log(
                                LogLevel.WARN,
                                "Repository: '${it.name}' is not a Maven repository and so is not currently supported in extensions."
                            )

                            if (it is DefaultMavenLocalArtifactRepository)
                                logger.log(
                                    LogLevel.WARN,
                                    "Maven repository: '${it.name}' at '${it.url}' is a local repo. While currently supported, this is a bad practice and should be removed in production builds."
                                )
                        }
                        .filterIsInstance<MavenArtifactRepository>()
                        .associateBy { it.url }
                        .map { it.value }
                        .map {
                            if (it.credentials.password != null || it.credentials.username != null)
                                throw UnsupportedOperationException("Using credentials in repositories are not supported yet!")

                            val b = it is DefaultMavenLocalArtifactRepository
                            val type = if (b) "local" else "default"
                            val location = if (b) it.url.path else it.url.toString()
                            val settings = mutableMapOf(
                                "location" to location.removeSuffix("/"),
                                "type" to type
                            )
                            ExtensionRepository(
                                "simple-maven",
                                settings
                            )
                        }

                    fun Sequence<String>.mapDependencies(): List<Map<String, String>> =
                        map(project.configurations::named)
                            .map(NamedDomainObjectProvider<Configuration>::get)
                            .flatMap(Configuration::getDependencies)
                            .mapNotNull(::ermDependency)
                            .associateBy {
                                it["descriptor"]
                            } // We do this to filter duplicates, anything that has the same descriptor has to go.
                            .map { it.value }


                    yakclient.versionPartitionHandler.partitions
                        .asSequence()
                        .map(YakClientExtension.VersionPartition::extensionConfigurationName)
                        .mapDependencies()
                        .forEach(yakclient.erm.extensions::add)

                    yakclient.erm.extensionRepositories.addAll(extensionRepositories.map(ExtensionRepository::settings))

                    yakclient.erm.versionPartitions.addAll(yakclient.versionPartitionHandler.partitions.map {
                        ExtensionVersionPartition(
                            it.name,
                            "META-INF/versioning/partitions/${it.name}",
                            it.supportedVersions.toMutableSet(),
                            extensionRepositories.toMutableList(),
                            listOf(
                                it.sourceSet.apiConfigurationName,
                                it.sourceSet.runtimeOnlyConfigurationName,
                                it.sourceSet.implementationConfigurationName
                            ).asSequence().mapDependencies().toMutableList(),
                            mutableListOf()
                        )
                    })

                    yakclient.erm.mainPartition = yakclient.versionPartitionHandler.main.name
                }
            })
        }

        val jar = project.tasks.named("jar", Jar::class.java) { jar ->
            jar.dependsOn(configureYak)
            jar.dependsOn(generateErm)
            jar.from(generateErm)

            val outputs = yakclient.versionPartitionHandler.partitions

            outputs.forEach { partition ->
                jar.from(partition.sourceSet.output) { copy ->
                    copy.into("META-INF/versioning/partitions/${partition.name}")
                }
            }
        }
        project.tasks.register("launch", LaunchTask::class.java) {
            it.dependsOn(configureYak)
            it.dependsOn(jar)
        }

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

    init {
//        project. {
//            erm.groupId = it.group.toString()
//            erm.name = it.name
//            erm.version = project.version.toString()
//        }
    }

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