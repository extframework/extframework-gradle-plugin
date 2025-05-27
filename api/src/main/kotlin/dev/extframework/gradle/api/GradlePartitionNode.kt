package dev.extframework.gradle.api

import dev.extframework.archives.ArchiveHandle
import dev.extframework.tooling.api.extension.partition.ExtensionPartition
import dev.extframework.tooling.api.extension.partition.PartitionAccessTree
import java.nio.file.Path
import kotlin.reflect.KClass

public class GradlePartitionNode(
    override val archive: ArchiveHandle,
    override val access: PartitionAccessTree,
    public val entrypoint: KClass<out GradleEntrypoint>,
    public val jarPath: Path
) : ExtensionPartition