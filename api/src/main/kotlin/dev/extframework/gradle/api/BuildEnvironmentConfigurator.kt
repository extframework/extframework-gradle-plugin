package dev.extframework.gradle.api

import com.durganmcbroom.jobs.Job
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.IArchive
import dev.extframework.boot.monad.Tagged
import dev.extframework.tooling.api.environment.MutableObjectSetAttribute
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings

public val environmentConfigurators: MutableObjectSetAttribute.Key<BuildEnvironmentConfigurator> =
    MutableObjectSetAttribute.Key<BuildEnvironmentConfigurator>("environment-configurators")

public interface BuildEnvironmentConfigurator {
    /**
     * Configures environments. This could include anything operation that is generally run in this
     * environment, however an easy example is the loading of partitions.
     */
    public fun configure(
        environment: BuildEnvironment,
        helper: Helper
    ) : Job<Unit>

    public interface Helper {
        public val repository: ExtensionRepositorySettings

        public fun attachDependencies(
            partition: PartitionHandler<*>,
            list: List<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>
        )
    }
}