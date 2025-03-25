package dev.extframework.gradle.partition

import dev.extframework.archives.ArchiveHandle
import dev.extframework.gradle.api.GradleEntrypoint
import dev.extframework.tooling.api.extension.partition.ExtensionPartition
import dev.extframework.tooling.api.extension.partition.PartitionAccessTree
import java.nio.file.Path
import kotlin.reflect.KClass

class GradlePartitionNode(
    override val archive: ArchiveHandle,
    override val access: PartitionAccessTree,
    val entrypoint: KClass<out GradleEntrypoint>,
    val jarPath: Path
) : ExtensionPartition