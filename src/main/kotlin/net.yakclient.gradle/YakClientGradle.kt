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
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.jvm.tasks.Jar
import kotlin.reflect.KProperty

class YakClientGradle : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(JvmEcosystemPlugin::class.java)
        val yakclient = project.extensions.create("yakclient", YakClientExtension::class.java, project)

        val logger = project.logger

        project.dependencies.add("implementation", "net.yakclient:client-api:1.0-SNAPSHOT")

        val generateErm = project.tasks.register("generateErm", GenerateErm::class.java) { task ->
            val sourceSets = yakclient.sourceSets

            sourceSets.forEach {
                task.dependsOn(project.tasks.named(it.classesTaskName).get())
            }

            task.inputFiles.set(
                project.files(
                    sourceSets
                        .map(SourceSet::getOutput)
                        .flatMap(SourceSetOutput::getFiles)
                )
            )

            task.doFirst {
                project.repositories
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
                    .forEach {
                        if (it.credentials.password != null || it.credentials.username != null)
                            throw UnsupportedOperationException("Using credentials in repositories are not supported yet!")

                        val b = it is DefaultMavenLocalArtifactRepository
                        val type = if (b) "local" else "default"
                        val location = if (b) it.url.path else it.url.toString()
                        val settings = mutableMapOf(
                            "location" to location.removeSuffix("/"),
                            "type" to type
                        )
                        val r = ErmRepository(
                            "simple-maven",
                            settings
                        )

                        yakclient.erm.dependencyRepositories.add(r)
                        yakclient.erm.extensionRepositories.add(settings)
                    }

                fun Sequence<String>.mapDependencies(): List<Map<String, String>> =
                    map(project.configurations::named)
                        .map(NamedDomainObjectProvider<Configuration>::get)
                        .flatMap(Configuration::getDependencies)
                        .mapNotNull(::ermDependency)
                        .associateBy { it["descriptor"] } // We do this to filter duplicates, anything that has the same descriptor has to go.
                        .map { it.value }


                yakclient.versionPartitionHandler.partitions
                    .asSequence()
                    .map(YakClientExtension.VersionPartition::extensionConfigurationName)
                    .mapDependencies()
                    .forEach(yakclient.erm.extensions::add)

                yakclient.sourceSets
                    .asSequence()
                    .flatMap {
                        listOf(
                            it.apiConfigurationName,
                            it.runtimeOnlyConfigurationName,
                            it.implementationConfigurationName
                        )
                    }
                    .mapDependencies()
                    .forEach(yakclient.erm.dependencies::add)
            }

            yakclient.erm.versioningPartitions += yakclient.versionPartitionHandler.partitions
                .flatMap { p -> p.supportedVersions.map { it to p } }
                .groupBy { it.first }
                .mapValues { it.value.map { it.second.name } }

            yakclient.erm.versioningPartitions
        }

        project.tasks.named("jar", Jar::class.java) { jar ->
            jar.dependsOn(generateErm)
            jar.from(generateErm)

            val outputs = yakclient.versionPartitionHandler.partitions

            outputs.forEach { partition ->
                jar.from(partition.sourceSet.output) { copy ->
                    copy.into("META-INF/versioning/partitions/${partition.name}")
                }
            }

            jar.eachFile {
                println(it.name)
                if (it.name.endsWith("mixin-annotations.json")) {

                }
            }
        }
    }
}

open class YakClientExtension(
    private val project: Project
) {
    internal val versionPartitionHandler = VersionPartitionHandler()
    internal val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
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
            val sourceSet = sourceSets.create(name)

            val versionPartition = VersionPartition(
                project,
                name,
                sourceSet,
                ArrayList(),
                false,
                "${name}Extension"
            )

            project.dependencies.add(
                sourceSet.implementationConfigurationName,
                versionPartition.dependencyScope.other("main")
            )

            project.configurations.create(versionPartition.extensionConfigurationName) {
                it.extendsFrom(project.configurations.named(sourceSet.implementationConfigurationName).get())
            }

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
        val extensionConfigurationName: String
    ) {
        internal val dependencyScope = DependencyScope()

        fun dependencies(action: Action<DependencyScope>) = action.execute(dependencyScope)

        inner class DependencyScope {
            internal val minecraftDependencies = ArrayList<String>()

            fun other(name: String): SourceSetOutput {
                return project.extensions.getByType(YakClientExtension::class.java).sourceSets.getByName(name).output
            }

            fun other(): GettingDelegate<SourceSetOutput> = GettingDelegate(::other)

            operator fun String.invoke(notation: Any) {
                project.dependencies.add(this, notation)
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
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return provider(property.name)
    }
}