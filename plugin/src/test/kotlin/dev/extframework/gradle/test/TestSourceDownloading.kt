package dev.extframework.gradle.test

import BootLoggerFactory
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMaven
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.launch
import dev.extframework.boot.archive.DefaultArchiveGraph
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.extloader.DefaultExtensionLoader
import dev.extframework.extloader.RootExtensionEnvironment
import dev.extframework.extloader.extension.DefaultExtensionResolver
import dev.extframework.gradle.api.source.SourceDependencyTypeContainer
import dev.extframework.gradle.source.MavenSourceProvider
import dev.extframework.gradle.source.MavenSourceResolver
import dev.extframework.gradle.source.SourcePartitionResolver
import dev.extframework.gradle.SourcesArchiveGraph
import dev.extframework.`object`.ObjectContainerImpl
import dev.extframework.tooling.api.environment.EnvironmentRegistry
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import dev.extframework.tooling.api.extension.partition.artifact.partition
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path
import kotlin.test.Test

class TestSourceDownloading {
    @Test
    fun `Test resolve maven sources`() {
        val maven = ResolutionContext(SimpleMaven)

        launch {
            val artifact = maven.getAndResolve(
                SimpleMavenArtifactRequest(
                    "dev.extframework:boot:3.6.2-SNAPSHOT:sources"
                ),
                SimpleMavenRepositorySettings.default(
                    url = "https://maven.extframework.dev/snapshots",

                    )
            )().merge()

            println(artifact)
        }
    }

    @Test
    fun `Test download maven sources`() {
        val resolver = MavenSourceResolver()
        val graph = DefaultArchiveGraph(Path("tests"))
        launch(BootLoggerFactory()) {
            graph.cache(
                SimpleMavenArtifactRequest(
                    "dev.extframework:boot:3.6.2-SNAPSHOT:sources"
                ),
                SimpleMavenRepositorySettings.default(
                    url = "https://maven.extframework.dev/snapshots"
                ),
                resolver
            )().merge()
        }
    }

    @Test
    fun `Test extension partition source download`() {
        val graph = SourcesArchiveGraph(Path("tests"))

        val registry: EnvironmentRegistry = ObjectContainerImpl()

        val dependencyTypes = DependencyTypeContainer(graph)
        var mavenProvider = MavenResolverProvider()
        dependencyTypes.register("simple-maven", mavenProvider)

        val loader = DefaultExtensionLoader(
            DefaultExtensionResolver(
                ClassLoader.getSystemClassLoader(),
                registry,
                "root"
            ),
            graph,
            RootExtensionEnvironment("root", Path("tests"), dependencyTypes),
            registry
        )

        val sourceResolver = SourcePartitionResolver(
            loader.extensionResolver.accessBridge,
            registry,
            "root"
        )

        loader.rootEnvironment += SourceDependencyTypeContainer(graph)
        loader.rootEnvironment[SourceDependencyTypeContainer].register(
            "simple-maven", MavenSourceProvider(mavenProvider)
        )

        launch(BootLoggerFactory()) {
            runBlocking {
                val descriptor = ExtensionDescriptor(
                    "dev.extframework.core",
                    "minecraft",
                    "1.0.3-BETA"
                )

                val repository = ExtensionRepositorySettings.default(
                    url = "https://repo.extframework.dev/registry",
                    true,
                    false
                )

                loader.cache(
                    mapOf(
                        descriptor to repository
                    )
                )().merge()

                loader.load(
                    listOf(
                        descriptor,
                    )
                )().merge()

                graph.cacheAsync(
                    PartitionArtifactRequest(descriptor.partition("tweaker", "root")),
                    repository,
                    sourceResolver
                )().merge()
            }
        }
    }
}