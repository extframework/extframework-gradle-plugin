package dev.extframework.gradle.api.source

import com.durganmcbroom.artifact.resolver.ArtifactRequest
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.dependency.DependencyResolverProvider

public interface DependencySourceProvider<R : ArtifactRequest<*>> {
    public val provider: DependencyResolverProvider<*, R, *>
    public val resolver: ArchiveNodeResolver<*, R, *, *, *>

    public fun tagSource(request: R) : R
}