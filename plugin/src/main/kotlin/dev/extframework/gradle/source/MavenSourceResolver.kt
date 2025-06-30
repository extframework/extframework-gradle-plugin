package dev.extframework.gradle.source

import com.durganmcbroom.artifact.resolver.ArtifactRepository
import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.ResourceNotFoundException
import dev.extframework.boot.archive.*
import dev.extframework.boot.dependency.DependencyResolverProvider
import dev.extframework.boot.maven.MavenLikeResolver
import dev.extframework.boot.monad.Either
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.util.mapAsync
import dev.extframework.gradle.api.source.DependencySourceProvider
import kotlinx.coroutines.awaitAll
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
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

    override val id: String by provider::id
}

class MavenSourceResolver :
    MavenLikeResolver<SourceDependencyNode<SimpleMavenDescriptor>, SimpleMavenArtifactMetadata> {
    override val id: String = "simple-maven:sources"
    override val nodeType: Class<in SourceDependencyNode<SimpleMavenDescriptor>>
        get() = SourceDependencyNode::class.java
    override val metadataType: Class<SimpleMavenArtifactMetadata>
        get() = SimpleMavenArtifactMetadata::class.java
    override val factory: RepositoryFactory<SimpleMavenRepositorySettings, ArtifactRepository<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactMetadata>>
        get() = SimpleMaven

    override fun pathForDescriptor(descriptor: SimpleMavenDescriptor, classifier: String, type: String): Path {
        return Path(
            descriptor.group.replace('.', File.separatorChar),
            descriptor.artifact,
            descriptor.version,
            "${descriptor.artifact}-${descriptor.version}-$classifier.$type"
        )
    }

    override fun load(
        data: ArchiveData<SimpleMavenDescriptor, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): SourceDependencyNode<SimpleMavenDescriptor> {
        throw Exception("Cannot load a source node")
    }

    override suspend fun cache(
        metadata: SimpleMavenArtifactMetadata,
        parents: List<Tree<Either<SimpleMavenArtifactMetadata, TaggedIArchive>>>,
        helper: CacheHelper<SimpleMavenDescriptor>
    ): Tree<TaggedIArchive> {
        // TODO error message for trying to load non-source jars
        try {
            val byteArray = metadata.jar()?.let {
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
            System.err.println("Encountered error when downloading sources for '${metadata.descriptor}'. " + e.message)
        }

        return helper.newData(
            metadata.descriptor,
            parents.mapAsync {
                helper.cache(
                    it, this@MavenSourceResolver,
                )
            }.awaitAll()
        )
    }
}