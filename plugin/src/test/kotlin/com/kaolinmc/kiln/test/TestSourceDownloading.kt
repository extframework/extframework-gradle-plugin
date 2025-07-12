package com.kaolinmc.kiln.test

import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMaven
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.kaolinmc.boot.archive.DefaultArchiveGraph
import com.kaolinmc.boot.dependency.DependencyTypeContainer
import com.kaolinmc.boot.maven.MavenResolverProvider
import com.kaolinmc.extloader.DefaultExtensionEnvironment
import com.kaolinmc.extloader.DefaultExtensionLoader
import com.kaolinmc.extloader.extension.DefaultExtensionResolver
import com.kaolinmc.kiln.SourcesArchiveGraph
import com.kaolinmc.kiln.api.source.sourceDependencyTypesAttrKey
import com.kaolinmc.kiln.source.MavenSourceProvider
import com.kaolinmc.kiln.source.MavenSourceResolver
import com.kaolinmc.kiln.source.SourcePartitionResolver
import com.kaolinmc.`object`.ObjectContainerImpl
import com.kaolinmc.tooling.api.environment.ObjectContainerAttribute
import com.kaolinmc.tooling.api.extension.artifact.ExtensionDescriptor
import com.kaolinmc.tooling.api.extension.artifact.ExtensionRepositorySettings
import com.kaolinmc.tooling.api.extension.partition.artifact.PartitionArtifactRequest
import com.kaolinmc.tooling.api.extension.partition.artifact.partition
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path
import kotlin.test.Test

class TestSourceDownloading {
    @Test
    fun `Test resolve maven sources`() {
        val maven = ResolutionContext(SimpleMaven)

        runBlocking {
            val artifact = maven.getAndResolveAsync(
                SimpleMavenArtifactRequest(
                    "com.kaolinmc:boot:3.6.2-SNAPSHOT:sources"
                ),
                SimpleMavenRepositorySettings.default(
                    url = "https://maven.kaolinmc.com/snapshots",

                    )
            )

            println(artifact)
        }
    }

    @Test
    fun `Test download maven sources`() {
        val resolver = MavenSourceResolver()
        val graph = DefaultArchiveGraph(Path("tests"))
        runBlocking {
            graph.cache(
                SimpleMavenArtifactRequest(
                    "com.kaolinmc:boot:3.6.2-SNAPSHOT:sources"
                ),
                SimpleMavenRepositorySettings.default(
                    url = "https://maven.kaolinmc.com/snapshots"
                ),
                resolver
            )
        }
    }

    @Test
    fun `Test extension partition source download`() {
        val graph = SourcesArchiveGraph(Path("tests"))

        val dependencyTypes: DependencyTypeContainer = ObjectContainerImpl()
        val mavenProvider = MavenResolverProvider()
        dependencyTypes.register(mavenProvider)

        val root = DefaultExtensionEnvironment("root")


        val loader = DefaultExtensionLoader(
            DefaultExtensionResolver(
                ClassLoader.getSystemClassLoader(),
                root,
            ),
            graph,
            root,
        )

        val sourceResolver = SourcePartitionResolver(
            root
        )

        root += ObjectContainerAttribute(sourceDependencyTypesAttrKey)
        root[sourceDependencyTypesAttrKey].container.register(
            MavenSourceProvider(mavenProvider)
        )

        runBlocking {
            val descriptor = ExtensionDescriptor(
                "com.kaolinmc.core",
                "minecraft",
                "1.0.3-BETA"
            )

            val repository = ExtensionRepositorySettings.default(
                url = "https://repo.kaolinmc.com/registry",
                true,
                false,
//                requireResourceVerification = true
            )

            loader.cache(
                mapOf(
                    descriptor to repository
                )
            )

            loader.load(
                listOf(
                    descriptor,
                )
            )

            graph.cache(
                PartitionArtifactRequest(descriptor.partition("tweaker")),
                repository,
                sourceResolver
            )
        }
    }
}