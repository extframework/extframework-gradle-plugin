package com.kaolinmc.kiln.api.source

import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.kaolinmc.boot.archive.ArchiveNodeResolver
import com.kaolinmc.boot.dependency.DependencyResolverProvider
import com.kaolinmc.`object`.ObjectContainer

public interface DependencySourceProvider<R : ArtifactRequest<*>> : ObjectContainer.IDed {
    public val provider: DependencyResolverProvider<*, R, *>
    public val resolver: ArchiveNodeResolver<*, R, *, *, *>

    public fun tagSource(request: R) : R
}