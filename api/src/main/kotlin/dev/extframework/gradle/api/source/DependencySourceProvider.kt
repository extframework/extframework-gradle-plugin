package dev.extframework.gradle.api.source

import com.durganmcbroom.artifact.resolver.ArtifactRequest
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.dependency.DependencyResolverProvider
import dev.extframework.`object`.ObjectContainer

public interface DependencySourceProvider<R : ArtifactRequest<*>> : ObjectContainer.IDed {
    public val provider: DependencyResolverProvider<*, R, *>
    public val resolver: ArchiveNodeResolver<*, R, *, *, *>

    public fun tagSource(request: R) : R
}