package com.kaolinmc.kiln.source

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.kaolinmc.boot.archive.ArchiveAccessTree
import com.kaolinmc.boot.archive.ArchiveNode

class SourceDependencyNode<T: ArtifactMetadata.Descriptor>(
    override val access: ArchiveAccessTree,
    override val descriptor: T
) : ArchiveNode<T>