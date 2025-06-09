package dev.extframework.gradle.source

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.ResourceNotFoundException
import dev.extframework.boot.archive.*
import dev.extframework.boot.dependency.DependencyResolverProvider
import dev.extframework.boot.maven.MavenLikeResolver
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.common.util.resolve
import dev.extframework.gradle.api.source.DependencySourceProvider
import kotlinx.coroutines.awaitAll
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.Path

class MavenSourceProvider(
    override val provider: DependencyResolverProvider<*, SimpleMavenArtifactRequest, *>
) : DependencySourceProvider<SimpleMavenArtifactRequest> {
    override val resolver: ArchiveNodeResolver<*, SimpleMavenArtifactRequest, *, *, *> = MavenSourceResolver()

    override fun tagSource(request: SimpleMavenArtifactRequest): SimpleMavenArtifactRequest {
        return SimpleMavenArtifactRequest(
            request.descriptor.copy(
                classifier = "sources"
            )
        )
    }
}

class MavenSourceResolver :
    MavenLikeResolver<SourceDependencyNode<SimpleMavenDescriptor>, SimpleMavenArtifactMetadata> {
    override val name: String = "simple-maven:sources"
    override val nodeType: Class<in SourceDependencyNode<SimpleMavenDescriptor>>
        get() = SourceDependencyNode::class.java
    override val metadataType: Class<SimpleMavenArtifactMetadata>
        get() = SimpleMavenArtifactMetadata::class.java
    override val context: ResolutionContext<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactMetadata> =
        ResolutionContext(SimpleMaven)

    override fun load(
        data: ArchiveData<SimpleMavenDescriptor, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): Job<SourceDependencyNode<SimpleMavenDescriptor>> = job {
        throw Exception("Cannot load a source node")
    }

    override fun cache(
        artifact: Artifact<SimpleMavenArtifactMetadata>,
        helper: CacheHelper<SimpleMavenDescriptor>
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        // TODO error message for trying to load non-source jars
        try {
            val byteArray = artifact.metadata.jar()?.let {
                ByteArrayOutputStream().use { bos ->
                    it.open().collect {
                        bos.write(it)
                    }

                    bos
                }.toByteArray()
            }

            if (byteArray != null) helper.withResource("jar-sources.jar", Resource("<heap>") {
                ByteArrayInputStream(byteArray)
            })
        } catch (e: ResourceNotFoundException) {
            // Nothing
        } catch (e: Exception) {
            // TODO this is bad design.
            System.err.println("Encountered error when downloading sources for '${artifact.metadata.descriptor}'. " + e.message)
        }

        helper.newData(
            artifact.metadata.descriptor,
            artifact.parents.mapAsync {
                helper.cache(
                    it, this@MavenSourceResolver,
                )().merge()
            }.awaitAll()
        )
    }
}