package dev.extframework.gradle.source

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveNode

class SourceDependencyNode<T: ArtifactMetadata.Descriptor>(
    override val access: ArchiveAccessTree,
    override val descriptor: T
) : ArchiveNode<T>