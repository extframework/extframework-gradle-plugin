package com.kaolinmc.kiln.api

import com.kaolinmc.archives.ArchiveHandle
import com.kaolinmc.tooling.api.extension.partition.ExtensionPartition
import com.kaolinmc.tooling.api.extension.partition.PartitionAccessTree
import java.nio.file.Path
import kotlin.reflect.KClass

public class GradlePartitionNode(
    override val archive: ArchiveHandle,
    override val access: PartitionAccessTree,
    public val entrypoint: KClass<out GradleEntrypoint>,
    public val jarPath: Path
) : ExtensionPartition