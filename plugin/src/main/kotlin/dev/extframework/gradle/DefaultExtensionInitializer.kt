package dev.extframework.gradle

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.archive.*
import dev.extframework.boot.dependency.DependencyResolverProvider
import dev.extframework.boot.maven.MavenConstraintNegotiator
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.toList
import dev.extframework.common.util.deleteAll
import dev.extframework.common.util.filterDuplicates
import dev.extframework.common.util.make
import dev.extframework.common.util.readInputStream
import dev.extframework.common.util.resolve
import dev.extframework.extloader.extension.partition.TweakerPartitionMetadata
import dev.extframework.extloader.extension.partition.TweakerPartitionNode
import dev.extframework.extloader.util.emptyArchiveReference
import dev.extframework.gradle.ExtframeworkPlugin.Companion.EXTFRAMEWORK_CENTRAL
import dev.extframework.gradle.api.*
import dev.extframework.gradle.api.source.SourceDependencyTypeContainer
import dev.extframework.gradle.partition.GradlePartitionLoader
import dev.extframework.gradle.partition.GradlePartitionMetadata
import dev.extframework.gradle.source.MavenSourceProvider
import dev.extframework.gradle.tasks.GenerateErm
import dev.extframework.gradle.util.removePrefix
import dev.extframework.gradle.util.setupProject
import dev.extframework.gradle.util.write
import dev.extframework.tooling.api.ExtensionLoader
import dev.extframework.tooling.api.environment.*
import dev.extframework.tooling.api.extension.ExtensionRepository
import dev.extframework.tooling.api.extension.ExtensionRuntimeModel
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.descriptor
import dev.extframework.tooling.api.extension.partition.ExtensionPartitionContainer
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor
import dev.extframework.tooling.api.extension.partition.artifact.partition
import dev.extframework.tooling.api.uber.*
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import java.io.File
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.writeBytes

// TODO any 'mocked' artifact (which currently are only maven ones and extensions) should be deleted
//   from the archives cache at the start of configuration because they have the possibility of having
//   an invalid access tree.
open class DefaultExtensionInitializer(
    root: Project
) : EnvironmentInitializer {
    private val mapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())

    override val dataDir = root.layout.projectDirectory.asFile.toPath() resolve ".extframework"

    val repoDir = dataDir resolve "mock-ext"
    val buildPath = dataDir resolve "buildpath"
    val buildFingerprint = buildPath resolve ".fingerprint.json"

    override var needsReload = false
    private val archiveDataCache = HashMap<ArtifactMetadata.Descriptor, ArchiveData<*, *>>()
    private val writtenMavenBuilds = HashSet<Project>()

    override var bootstrapped: Boolean = false
    override val managed: MutableSet<ExtframeworkExtension> = HashSet()

    init {
        check(root == root.rootProject) {
            "Invalid Extension worker invocation, can only be constructed with the root project."
        }
    }

    /**
     * Bootstrapping actions (CONFIGURATION INDEPENDENT):
     *  - Basic project setup
     *  - Tweaking the default environment to contain necessary configurators and dependency resolvers
     *  - Caching / loading parents (based on the configuration independent extension.toml config)
     *  - Loads the gradle partition (triggers reload)
     */
    override suspend fun bootstrap(
        extension: ExtframeworkExtension,
    ) {
        setupProject(extension)
        tweakRoot(extension)

        val descriptors = extension.configuration.parents
            .filterNot { it.value.isProjectBuild }
            .map { (name, attr) ->
                ExtensionDescriptor(
                    attr.group ?: throw IllegalArgumentException(
                        "Parent group cannot be null unless 'isBuild' is set."
                    ),
                    name,
                    attr.version ?: throw IllegalArgumentException(
                        "Parent version cannot be null unless 'isBuild' is set."
                    ),
                ) to attr.repository
            }

        extension.loader.cache(
            descriptors.associate { (descriptor, repository) ->
                descriptor to toRepository(extension.configuration, repository)
            }
        )

        val parents = extension.loader.load(descriptors.map { it.first })
        extension.build.parents.addAll(parents)

        val fingerprint = extension.loader.applyGradle(extension)

        extension.build.plugins += fingerprint.plugins
        extension.build.tweakers += fingerprint.tweakers
        extension.build.fingerprint += fingerprint.finger
        extension.build.content += fingerprint.content

        managed.add(extension)

        Unit
    }

    /**
     * Configuration actions (CONFIGURATION DEPENDENT)
     *
     *  ***Operates on gradle configuration to set up environment(s) for development**
     *
     *  - Applies gradle plugins
     *  - Tweaks `this` root environment using loaded gradle plugin. Not that tweaker partitions
     *  do not have access to the root environment of this extension as they only operate on emitted
     *  environments.
     *  - Emits build environments from the `root`
     *  - Tweaks emitted
     *  - Sets up partitions
     */
    private val configured = HashSet<ExtframeworkExtension>()
    override suspend fun configure(
        extension: ExtframeworkExtension
    ) {
        if (!configured.add(extension)) return

        val pluginObjects = extension.build.plugins.map {
            extension.project.plugins.apply(
                // At this point in configuration, gradle should handle class loading.
                extension.project.buildscript.classLoader.loadClass(it) as Class<GradleEntrypoint>
            )
        }

        pluginObjects.forEach { plugin ->
            plugin.tweak(extension.defaultEnvironment)
        }

        val environments = extension.defaultEnvironment.find(environmentEmitters)?.flatMap {
            it.emit(extension).map { env ->
                BuildEnvironment(env, extension)
            }
        } ?: listOf()

        for (environment in environments) {
            extension.loader.environmentRegistry.register(environment.name, environment)

            extension.loader.tweak(
                extension.build.parents,
                environment
            )
        }

        extension.environments += environments

        setupPartitions(extension)
    }

    private suspend fun setupPartitions(
        extension: ExtframeworkExtension,
    ) {
        val config = extension.configuration

        // Generate the runtime model for this extension
        val erm = amendRuntimeModelForProjects(extension, GenerateErm.setupModel(extension))
        val descriptor by erm::descriptor

        // Writing this extension and all maven builds to the repo
        writeMockExtension(
            erm
        )
        for (project in extension.project.rootProject.allprojects) {
            if (writtenMavenBuilds.add(project)) {
                writeMockMaven(project)
            }
        }

        val parentBuilds = config.parents.filter {
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

        for ((_, parentProject) in parentBuilds) {
            configure(
                parentProject
            )
        }

        val accessibleParents = parentBuilds + (extension.model.descriptor to extension)

        val accessibleBuilds = extension.project.rootProject.allprojects.flatMap { p ->
            buildList {
                val projectDescriptor = SimpleMavenDescriptor(
                    p.group as? String ?: return@buildList,
                    p.name,
                    p.version as? String ?: return@buildList,
                    null
                )

                add(projectDescriptor to p)
            } + (p.extensions.findByType(PublishingExtension::class.java)?.publications?.toList() ?: listOf())
                .filterIsInstance<MavenPublication>()
                .map {
                    SimpleMavenDescriptor(it.groupId, it.artifactId, it.version, null) to p
                }
        }.toMap()

        val repository = ExtensionRepositorySettings.local(
            path = repoDir.toString()
        )

        deleteExtensionMetadata(extension, descriptor)

        extension.loader.cache(
            mapOf(
                descriptor to repository
            )
        )

        extension.loader.load(
            listOf(descriptor),
        )

        for (environment in extension.environments) {
            val configurators = environment[environmentConfigurators]

            for (configurator in configurators) {
                configurator.configure(environment, object : BuildEnvironmentConfigurator.Helper {
                    override val repository: ExtensionRepositorySettings =
                        ExtensionRepositorySettings.local(
                            path = repoDir.toString()
                        )

                    override fun attachDependencies(
                        partition: PartitionHandler<*>,
                        classes: List<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>,
                    ) {
                        val gradleManaged = partition.model.dependencies.get().mapNotNull {
                            val req = it.evaluate() ?: return@mapNotNull null

                            environment[dependencyTypesAttrKey].container.get("simple-maven")?.parseRequest(
                                req
                            )?.descriptor as? SimpleMavenDescriptor
                        }

                        fun transform(
                            it: Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>
                        ): Iterable<DependencyType> {
                            val archive = it.value
                            val dependencyDescriptor = archive.descriptor

                            // Gradle handles this dependency already
                            if (gradleManaged.contains(dependencyDescriptor)) {
                                return listOf()
                            }

                            when (dependencyDescriptor) {
                                is PartitionDescriptor -> {
                                    val parentBuild = accessibleParents[dependencyDescriptor.extension]

                                    if (parentBuild != null) {
                                        val parentSourceSet =
                                            parentBuild.partitions.named(dependencyDescriptor.partition).get()

                                        return setOf(
                                            SourceSetDependency(parentSourceSet.sourceSet),
                                        )
                                    }
                                }

                                is SimpleMavenDescriptor -> {
                                    val build = accessibleBuilds[dependencyDescriptor]

                                    if (build != null) {
                                        return setOf(
                                            ProjectDependency(build)
                                        )
                                    }
                                }
                            }

                            return when (archive) {
                                is ArchiveData<*, *> -> {
                                    archive.resources.values
                                        .filterIsInstance<CachedArchiveResource>()
                                        .mapTo(HashSet()) { r ->
                                            PathDependency(r.path)
                                        }
                                }

                                is ExtensionPartitionContainer<*, *> -> {
                                    val node = archive.node
                                    when (node) { // These are the only 2 partitions that can be loaded at this point
                                        is GradlePartitionNode -> setOf(
                                            PathDependency(
                                                node.jarPath
                                            )
                                        )

                                        is TweakerPartitionNode -> setOf(
                                            PathDependency(
                                                node.jarPath
                                            )
                                        )

                                        else -> setOf()
                                    }
                                }

                                else -> {
                                    archiveDataCache[archive.descriptor]
                                        ?.resources
                                        ?.values
                                        ?.filterIsInstance<CachedArchiveResource>()
                                        ?.mapTo(HashSet()) { r ->
                                            PathDependency(r.path)
                                        } ?: setOf()
                                }
                            }
                        }

                        val classDependencies = classes
                            .toSet()
                            .flatMapTo(HashSet(), ::transform)

//                        val sourceDependencies = sources
//                            .toSet()
//                            .flatMapTo(HashSet(), ::transform)

                        for (dependency in classDependencies) {
                            extension.project.dependencies.add(
                                partition.sourceSet.implementationConfigurationName,
                                dependency.toNotation(extension)
                            )
                        }
                    }
                })
            }
        }
    }

    // TODO this is hacky, assumes directory layouts of archives.
    private fun deleteExtensionMetadata(extension: ExtframeworkExtension, descriptor: ExtensionDescriptor) {
        val pathForDescriptor = extension.loader.extensionResolver.pathForDescriptor(
            descriptor,
            "stub_for_deletion",
            "txt"
        )

        val classesPath = extension.loader.graph.path resolve pathForDescriptor
        classesPath.parent.deleteAll()

        val sourcesPath = extension.sourcesGraph.path resolve pathForDescriptor
        sourcesPath.parent.deleteAll()
    }

    // TODO better error messages: Right now if a library that we are writing has no publications
    //   it wont get published and a not found error will occur, instead we should warn or throw
    //   an error that communicates this behavior.
    private fun writeMockMaven(
        project: Project
    ) {
        val maven = project.extensions.findByType(PublishingExtension::class.java) ?: return

        maven.publications
            .filterIsInstance<MavenPublication>()
            .forEach {
                val path = repoDir resolve
                        it.groupId.replace('.', File.separatorChar) resolve
                        it.artifactId resolve
                        it.version

                val pomFile = path resolve "${it.artifactId}-${it.version}.pom"
                pomFile.make()
                pomFile.writeBytes(buildMavenPublication(it))
            }
    }

    private fun writeMockExtension(
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

    /**
     * Amends the runtime model to include references *first* to the mock repository
     * so that mocked artifacts are cached from there instead.
     */
    private fun amendRuntimeModelForProjects(
        extension: ExtframeworkExtension,
        model: ExtensionRuntimeModel
    ): ExtensionRuntimeModel {
        val additionalRepositories = extension.configuration.parents.filter {
            it.value.isProjectBuild
        }.map {
            mutableMapOf(
                "location" to repoDir.toString(),
                "type" to "local"
            )
        }

        return model.copy(
            repositories = (additionalRepositories + model.repositories),
            partitions = model.partitions.mapTo(HashSet()) {
                it.copy(
                    repositories = listOf(
                        ExtensionRepository(
                            "simple-maven", mutableMapOf(
                                "location" to repoDir.toString(),
                                "type" to "local"
                            )
                        )
                    ) + it.repositories
                )
            }
        )
    }

    private suspend fun ExtensionLoader.applyGradle(
        extension: ExtframeworkExtension
    ): BuildFingerprint {
        val resolver by extension.loader::extensionResolver

        val uberGradleParents = extension.build.parents
            .filter { archive ->
                val erm = resolver.accessBridge.ermFor(archive.descriptor)
                erm.partitions.any { model -> model.type == "gradle" }
            }
            .map { archive ->
                UberParentRequest(
                    PartitionArtifactRequest(
                        archive.descriptor,
                        "gradle",
                        rootEnvironment.name
                    ),
                    resolver.accessBridge.repositoryFor(archive.descriptor),
                    resolver.partitionResolver
                )
            }

        val uberTweakerParents = extension.build.parents
            .filter { archive ->
                val erm = resolver.accessBridge.ermFor(archive.descriptor)
                erm.partitions.any { model -> model.type == "tweaker" }
            }
            .map { archive ->
                UberParentRequest(
                    PartitionArtifactRequest(
                        archive.descriptor,
                        "tweaker",
                        rootEnvironment.name
                    ),
                    resolver.accessBridge.repositoryFor(archive.descriptor),
                    resolver.partitionResolver
                )
            }

        val uberDescriptor = UberDescriptor("Gradle Bootstrap")
        val uberRequest = UberArtifactRequest(
            uberDescriptor,
            uberGradleParents + uberTweakerParents,
        )

        val cacheResult = graph.cache(
            uberRequest,
            UberRepositorySettings,
            UberResolver
        ).toList()
//
//            val uberTweakerDescriptor = UberDescriptor("All Tweaker partitions")
//            val uberTweakerRequest = UberArtifactRequest(
//                uberTweakerDescriptor,
//                uberTweakerParents,
//            )
//
//            val tweakerCacheResult = graph.cacheAsync(
//                uberTweakerRequest,
//                UberRepositorySettings,
//                UberResolver
//            )().merge().toList()
//
//            val cacheResult = tweakerCacheResult + gradleCacheResult

        for (tagged in cacheResult) {
            val archive = tagged.value
            if (archive is ArchiveData<*, *>) {
                archiveDataCache[tagged.value.descriptor] = archive
            }
        }

        val fingerprint = buildFingerprint.takeIf {
            it.exists()
        }?.let {
            runCatching {
                mapper.readValue<MutableMap<String, BuildFingerprint>>(it.toFile())
            }.getOrNull()
        } ?: HashMap()

        val currentFingerprint = extension.build.parents.mapTo(HashSet()) { it.descriptor.name }

        if (fingerprint[extension.project.path]?.finger != currentFingerprint) {
            val uberGradle = graph.get(
                uberDescriptor,
                UberResolver
            )

            val accessSet = uberGradle.access.targets.mapTo(mutableSetOf()) { target ->
                target.descriptor
            }

            val filteredArchiveTree = cacheResult
                .filter { accessSet.contains(it.value.descriptor) }

            val projectBuildPath =
                buildPath resolve extension.project.path.replace(":", "_")
            projectBuildPath.deleteAll()

            for ((archive, resolver) in filteredArchiveTree) {
                val archive = archive as? ArchiveData<*, *>
                    ?: archiveDataCache[archive.descriptor]
                    ?: throw Exception("Archive loaded?")

                resolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>

                for ((name, resource) in archive.resources) {
                    resource as CachedArchiveResource

                    val (name, type) = name.split(".")
                    if (type != "jar") continue

                    val path = projectBuildPath resolve resolver.pathForDescriptor(archive.descriptor, name, type)
                    path.make()

                    resource.path.copyTo(path, overwrite = true)
                }
            }

            val plugins = extension.build.parents
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
                .map { it.metadata }
                .filterIsInstance<GradlePartitionMetadata>()
                .map { it.entrypoint }

            val tweakers = extension.build.parents
                .mapNotNull { archive ->
                    uberGradle.access
                        .targets
                        .map { target -> target.relationship.node }
                        .filterIsInstance<ExtensionPartitionContainer<*, *>>()
                        .filter { it.node is TweakerPartitionNode }
                        .filterIsInstance<ExtensionPartitionContainer<TweakerPartitionNode, *>>()
                        .find { container -> container.descriptor.extension == archive.descriptor }
                }
                .reversed()
                .filterDuplicates()
                .map { it.metadata }
                .filterIsInstance<TweakerPartitionMetadata>()
                .map { it.tweakerClass }

            fingerprint[extension.project.path] = BuildFingerprint(
                currentFingerprint,
                filteredArchiveTree.mapTo(HashSet()) {
                    it.value.descriptor.name
                },
                plugins,
                tweakers
            )
            buildFingerprint.make()

            buildFingerprint.writeBytes(
                mapper.writeValueAsBytes(fingerprint)
            )

            needsReload = true
        }

        return fingerprint[extension.project.path]!!
    }

    private suspend fun tweakRoot(
        extension: ExtframeworkExtension
    ) {
        val env = extension.defaultEnvironment

        env += SourceDependencyTypeContainer(extension.sourcesGraph)
        env[SourceDependencyTypeContainer].register(
            "simple-maven",
            MavenSourceProvider(
                env[dependencyTypesAttrKey].container.get("simple-maven")
                        as DependencyResolverProvider<*, SimpleMavenArtifactRequest, *>
            )
        )

        env[partitionLoadersAttrKey]
            .container
            .register("gradle", GradlePartitionLoader())

        env.set(MutableObjectSetAttribute(environmentEmitters))

        env.set(MutableObjectSetAttribute(environmentConfigurators))

        env[environmentConfigurators] += object : BuildEnvironmentConfigurator {
            override suspend fun configure(
                environment: BuildEnvironment,
                helper: BuildEnvironmentConfigurator.Helper
            ) {
                val tweaker = environment.extension.partitions.findByName("tweaker")
                if (tweaker != null) {
                    val partitionRequest = PartitionArtifactRequest(
                        environment.extension.model.descriptor.partition(
                            "tweaker",
                            environment.extension.defaultEnvironment.name
                        )
                    )

                    // ------ Dependencies ------
                    val dependencies = environment.extension.loader.graph.cache(
                        partitionRequest,
                        helper.repository,
                        environment.extension.loader.extensionResolver.partitionResolver
                    ).parents.flatMap { it.toList() }

                    helper.attachDependencies(
                        tweaker,
                        dependencies
                    )

                    // ------ Sources ------
                    environment.extension.sourcesGraph.cache(
                        partitionRequest,
                        helper.repository,
                        environment.extension.partitionSourceResolver
                    ).parents.flatMap { it.toList() }
                }

                val gradle = environment.extension.partitions.findByName(
                    "gradle"
                )

                if (gradle != null) {
                    val partitionRequest = PartitionArtifactRequest(
                        environment.extension.model.descriptor.partition(
                            "gradle",
                            environment.extension.defaultEnvironment.name
                        )
                    )

                    // ------ Dependencies ------
                    val dependencies = environment.extension.loader.graph.cache(
                        partitionRequest,
                        helper.repository,
                        environment.extension.loader.extensionResolver.partitionResolver
                    ).parents.flatMap { it.toList() }

                    helper.attachDependencies(
                        gradle,
                        dependencies
                    )

                    // ------ Sources ------
                    environment.extension.sourcesGraph.cache(
                        partitionRequest,
                        helper.repository,
                        environment.extension.partitionSourceResolver
                    ).parents.flatMap { it.toList() }
                }
            }
        }
    }

    private val mavenConstraintNegotiator = MavenConstraintNegotiator()
    private val packagedDependencies = parsePackagedDependencies().mapTo(HashSet()) {
        mavenConstraintNegotiator.classify(it)
    }

    private fun isDescriptorPackaged(
        descriptor: ArtifactMetadata.Descriptor
    ): Boolean {
        return packagedDependencies.contains(
            mavenConstraintNegotiator.classify(
                descriptor as? SimpleMavenDescriptor ?: return false
            )
        )
    }

    private fun parsePackagedDependencies(): Set<SimpleMavenDescriptor> {
        val dependencies: java.util.HashSet<SimpleMavenDescriptor> =
            ExtframeworkPlugin::class.java.getResourceAsStream("/dependencies.txt")?.use {
                val fileStr = String(it.readInputStream())
                fileStr.split("\n").toSet()
            }?.filterNot { it.isBlank() }?.mapTo(HashSet()) { SimpleMavenDescriptor.parseDescription(it)!! }
                ?: throw IllegalStateException("Cant load dependencies?")

        return dependencies
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
        fun toNotation(extension: ExtframeworkExtension): Any
    }

    private data class SourceSetDependency(
        val sourceSet: SourceSet,
    ) : DependencyType {
        override fun toNotation(extension: ExtframeworkExtension): Any {
            return sourceSet.output
        }
    }

    private data class ProjectDependency(
        val project: Project,
    ) : DependencyType {
        override fun toNotation(extension: ExtframeworkExtension): Any {
            return this.project
        }
    }

    private data class PathDependency(
        val path: Path,
    ) : DependencyType {
        override fun toNotation(extension: ExtframeworkExtension): Any {
            // TODO in this configuration maven local sources wont ever get attached.
            if (path.startsWith(extension.loader.graph.path)) {
                val relativePath = path.removePrefix(extension.loader.graph.path)
                val extension = relativePath.extension

                // TODO im not sure if this is expected behaviour from gradle:
                //  We are using '/'s to denote the file path of the jar where generally this
                //  would be something you pass in the group id (but we arent using a pom
                //  and adding a group id / version requires a pom to be present in the flat dir)
                return mapOf(
                    "name" to relativePath.toString().removeSuffix(".$extension")
                )
            }
            return extension.project.files(path)
        }
    }

    private data class BuildFingerprint(
        val finger: Set<String>,
        val content: Set<String>,
        val plugins: List<String>,
        val tweakers: List<String>
    )
}
