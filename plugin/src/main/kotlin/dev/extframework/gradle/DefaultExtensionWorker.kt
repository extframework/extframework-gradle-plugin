package dev.extframework.gradle

import BootLoggerFactory
import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.launch
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.archive.ArchiveData
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.CachedArchiveResource
import dev.extframework.boot.monad.map
import dev.extframework.boot.monad.toList
import dev.extframework.common.util.filterDuplicates
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.extloader.extension.partition.TweakerPartitionNode
import dev.extframework.extloader.util.emptyArchiveReference
import dev.extframework.gradle.ExtframeworkPlugin.Companion.EXTFRAMEWORK_CENTRAL
import dev.extframework.gradle.api.ExtensionWorker
import dev.extframework.gradle.api.ExtframeworkExtension
import dev.extframework.gradle.api.ExtensionConfig
import dev.extframework.gradle.api.GradleEntrypoint
import dev.extframework.gradle.partition.GradlePartitionLoader
import dev.extframework.gradle.partition.GradlePartitionNode
import dev.extframework.gradle.tasks.GenerateErm
import dev.extframework.gradle.util.write
import dev.extframework.tooling.api.ExtensionLoader
import dev.extframework.tooling.api.environment.*
import dev.extframework.tooling.api.extension.ExtensionResolver
import dev.extframework.tooling.api.extension.ExtensionRuntimeModel
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.descriptor
import dev.extframework.tooling.api.extension.partition.ExtensionPartitionContainer
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor
import dev.extframework.tooling.api.extension.partition.artifact.partitionNamed
import dev.extframework.tooling.api.uber.*
import kotlinx.coroutines.runBlocking
import org.apache.commons.io.FileUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import java.io.File
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

abstract class DefaultExtensionWorker(
    val root: Project
) : ExtensionWorker {
    private val mapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())

    override val dataDir = root.layout.projectDirectory.asFile.toPath() resolve ".extframework"
    override val loader: ExtensionLoader = ExtensionLoader(dataDir)
    val repoDir = dataDir resolve "mock-ext"
    val buildPath = dataDir resolve "buildpath"
    val buildFingerprint = buildPath resolve ".fingerprint.json"

    override val plugins: MutableList<Class<out Plugin<*>>> = ArrayList()

    override var needsReload = false
    private val archiveDataCache = HashMap<ArtifactMetadata.Descriptor, ArchiveData<*, *>>()
    private val setupPlugins = HashSet<Class<*>>()

    override var initialized: Boolean = false

    init {
        check(root == root.rootProject) {
            "Invalid Extension worker invocation, can only be constructed with the root project."
        }
    }

    override fun tweak(): AsyncJob<Unit> = asyncJob {
        val pluginObjects = HashSet<GradleEntrypoint>()

        for (project in root.allprojects) {
            val path = project.layout.projectDirectory.asFile.toPath() resolve "extension.toml"
            if (!path.exists()) continue

            for (klass in plugins) {
                val obj = project.plugins.apply(klass)

                if (setupPlugins.add(klass)) {
                    pluginObjects.add(obj as GradleEntrypoint)
                }
            }
        }

        for (entrypoint in pluginObjects) {
            entrypoint.setup(loader.environment)().merge()
        }

        loader.runTweakers()().merge()
    }

    override fun setupPartitions(
        extension: ExtframeworkExtension,
    ): AsyncJob<Unit> = asyncJob {
        val config = extension.configuration

        // Generate the runtime model for this extension
        val erm = amendRuntimeModelForProjects(extension, GenerateErm.setupModel(extension))
        val descriptor by erm::descriptor

        writeToMockRepository(
            erm
        )

        val projectParents = config.parents.filter {
            it.value.isProjectBuild
        }.entries.associate {
            val parentProject = extension.project.project(it.key)
            val parentExtension = parentProject.extensions.getByType(ExtframeworkExtension::class.java)
            val parentDescriptor = ExtensionDescriptor(
                parentExtension.model.groupId.get(),
                parentExtension.model.name.get(),
                parentExtension.model.version.get()
            )

            parentDescriptor to parentExtension
        }

        for ((_, parentProject) in projectParents) {
            setupPartitions(
                parentProject
            )().merge()
        }

        val repository = ExtensionRepositorySettings.local(
            path = repoDir.toString()
        )

        loader.cache(
            descriptor,
            repository
        )().merge()

        loader.load(
            listOf(descriptor),
        )().merge()

        for (handler in extension.partitions) {
            val accessible = loader.environment[ArchiveGraphAttribute].extract().graph.cache(
                PartitionArtifactRequest(descriptor.partitionNamed(handler.name)),
                repository,
                loader.environment[ExtensionResolver].extract().partitionResolver
            )().merge()

            val dependencies = accessible
                .map { it.value }
                .parents
                .flatMap {
                    it.toList()
                }
                .flatMapTo(HashSet()) {
                    val dependencyDescriptor = it.descriptor

                    if (dependencyDescriptor is PartitionDescriptor) {
                        val parentProject = projectParents[dependencyDescriptor.extension]

                        if (parentProject != null) {
                            val parentSourceSet =
                                parentProject.partitions.named(dependencyDescriptor.partition).get()

                            return@flatMapTo setOf(
                                SourceSetDependency(parentSourceSet.sourceSet)
                            )
                        }
                    }

                    when (it) {
                        is ArchiveData<*, *> -> {
                            it.resources.values
                                .filterIsInstance<CachedArchiveResource>()
                                .mapTo(HashSet()) {
                                    PathDependency(it.path)
                                }
                        }

                        is ExtensionPartitionContainer<*, *> -> {
                            val node = it.node
                            when (node) { // These are the only 2 partitions that can be loaded at this point
                                is GradlePartitionNode -> setOf(PathDependency(node.jarPath))
                                is TweakerPartitionNode -> setOf(PathDependency(node.jarPath))
                                else -> setOf()
                            }
                        }

                        else -> setOf()
                    }
                }

            for (dependency in dependencies) {
                extension.project.dependencies.add(
                    handler.sourceSet.implementationConfigurationName,
                    dependency.toNotation(extension.project)
                )
            }
        }
    }

    fun writeToMockRepository(
        erm: ExtensionRuntimeModel
    ) {
        val targetArchiveCache = dataDir resolve "extensions" resolve erm.groupId.replace(
            ".",
            File.separator
        ) resolve erm.name resolve erm.version

        if (targetArchiveCache.exists()) {
            val readInput: ExtensionRuntimeModel =
                mapper.readValue((targetArchiveCache resolve "${erm.name}-${erm.version}-erm.json").toFile())

            if (readInput != erm) {
                FileUtils.deleteDirectory(targetArchiveCache.toFile())
            }
        }

        val baseDir = repoDir resolve erm.groupId.replace(".", File.separator) resolve erm.name resolve erm.version

        val ermPath = baseDir resolve "${erm.name}-${erm.version}-erm.json"

        ermPath.make()
        ermPath.writeBytes(
            GenerateErm.writeErm(erm)
        )

        for (model in erm.partitions) {
            val partitionPath = baseDir resolve "${erm.name}-${erm.version}-${model.name}.jar"
            partitionPath.make()
            emptyArchiveReference().write(partitionPath)
        }
    }

    override fun initialize(
        extension: ExtframeworkExtension,
    ) = asyncJob {
        tweakForGradle(loader.environment)

        val descriptors = extension.configuration.parents
            .filterNot { it.value.isProjectBuild }
            .map { (name, attr) ->
                ExtensionDescriptor(
                    attr.group
                        ?: throw IllegalArgumentException("Parent group cannot be null unless 'isBuild' is set."),
                    name,
                    attr.version
                        ?: throw IllegalArgumentException("Parent version cannot be null unless 'isBuild' is set."),
                ) to attr.repository
            }

        for ((descriptor, repository) in descriptors) {
            loader.cache(
                descriptor,
                toRepository(extension.configuration, repository)
            )().merge()
        }

        loader.load(descriptors.map { it.first })().merge()

        val plugins = loader.applyGradle(extension.project)().merge()

        plugins.forEach {
            if (!this@DefaultExtensionWorker.plugins.contains(it.node.entrypoint.java)) {
                this@DefaultExtensionWorker.plugins.add(it.node.entrypoint.java)
            }
        }
    }

    private fun amendRuntimeModelForProjects(
        extension: ExtframeworkExtension,
        model: ExtensionRuntimeModel
    ): ExtensionRuntimeModel {
        val additionalRepositories = extension.configuration.parents.filter {
            it.value.isProjectBuild
        }.map { (name) ->
            mutableMapOf(
                "location" to repoDir.toString(),
                "type" to "local"
            )
        }

        return model.copy(
            repositories = (model.repositories + additionalRepositories)
        )
    }

    private fun ExtensionLoader.applyGradle(
        project: Project,
    ): AsyncJob<List<ExtensionPartitionContainer<GradlePartitionNode, *>>> =
        asyncJob {
            val resolver = environment[ExtensionResolver].extract()

            val uberGradleParents = loaded
                .filter { archive ->
                    val erm = resolver.accessBridge.ermFor(archive.descriptor)
                    erm.partitions.any { model -> model.type == "gradle" }
                }
                .map { archive ->
                    UberParentRequest(
                        PartitionArtifactRequest(
                            archive.descriptor,
                            "gradle",
                        ),
                        resolver.accessBridge.repositoryFor(archive.descriptor),
                        resolver.partitionResolver
                    )
                }

            val uberDescriptor = UberDescriptor("All Gradle partitions")
            val uberGradleRequest = UberArtifactRequest(
                uberDescriptor,
                uberGradleParents,
            )

            val cacheResult = environment.archiveGraph.cacheAsync(
                uberGradleRequest,
                UberRepositorySettings,
                UberResolver
            )().merge().toList()

            for ((archive) in cacheResult) {
                if (archive is ArchiveData<*, *>) {
                    archiveDataCache[archive.descriptor] = archive
                }
            }

            val uberGradle = environment.archiveGraph.get(
                uberDescriptor,
                UberResolver
            )().merge()

            val accessSet = uberGradle.access.targets.mapTo(mutableSetOf()) { target ->
                target.descriptor
            }

            val filteredArchiveTree = cacheResult
                .filter { accessSet.contains(it.value.descriptor) }

            val fingerprint = buildFingerprint.takeIf {
                it.exists()
            }?.let { mapper.readValue<BuildFingerprint>(it.toFile()) } ?: BuildFingerprint()

            val fingerprintContent = fingerprint.content[project.path] ?: setOf()

            val currentFingerprint = filteredArchiveTree.mapTo(HashSet()) {
                it.value.descriptor.name
            }

            if (fingerprintContent != currentFingerprint) {
                for ((archive, resolver) in filteredArchiveTree) {
                    val archive = archive as? ArchiveData<*, *> ?: archiveDataCache[archive.descriptor]
                    ?: throw Exception("Archive loaded?")

                    resolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>

                    for ((name, resource) in archive.resources) {
                        resource as CachedArchiveResource

                        val (name, type) = name.split(".")
                        if (type != "jar") continue

                        val path = buildPath resolve resolver.pathForDescriptor(archive.descriptor, name, type)
                        path.make()

                        resource.path.copyTo(path, overwrite = true)
                    }
                }

                fingerprint.content[project.path] = currentFingerprint
                buildFingerprint.make()

                buildFingerprint.writeBytes(
                    mapper.writeValueAsBytes(fingerprint)
                )

                needsReload = true
            }

            val plugins: List<ExtensionPartitionContainer<GradlePartitionNode, *>> = loaded
                .mapNotNull { archive ->
                    uberGradle.access
                        .targets
                        .map { target -> target.relationship.node }
                        .filterIsInstance<ExtensionPartitionContainer<*, *>>()
                        .filter { it.node is GradlePartitionNode }
                        .filterIsInstance<ExtensionPartitionContainer<GradlePartitionNode, *>>()
                        .find { container -> container.descriptor.extension == archive.descriptor }
                }
                .reversed()
                .filterDuplicates()

            plugins
        }

    private fun tweakForGradle(
        env: ExtensionEnvironment
    ) {
        env[partitionLoadersAttrKey].extract()
            .container
            .register("gradle", GradlePartitionLoader())
    }

    private fun toRepository(
        config: ExtensionConfig,
        name: String
    ): ExtensionRepositorySettings {
        return when (name) {
            "central" -> {
                ExtensionRepositorySettings.default(
                    url = EXTFRAMEWORK_CENTRAL
                )
            }

            "local" -> ExtensionRepositorySettings.local()
            else -> {
                val custom = config.repositories.custom[name]
                checkNotNull(custom) { "Invalid repository $name" }

                ExtensionRepositorySettings.default(
                    url = custom
                )
            }
        }
    }


    private sealed interface DependencyType {
        fun toNotation(project: Project): Any
    }

    private data class SourceSetDependency(
        val sourceSet: SourceSet,
    ) : DependencyType {
        override fun toNotation(project: Project): Any {
            return sourceSet.output
        }
    }

    private data class PathDependency(
        val path: Path
    ) : DependencyType {
        override fun toNotation(project: Project): Any {
            return project.files(path)
        }
    }

    private data class BuildFingerprint(
        val content: MutableMap<String, Set<String>> = HashMap()
    )
}
