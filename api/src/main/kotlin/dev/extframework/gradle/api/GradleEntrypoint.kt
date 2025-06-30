package dev.extframework.gradle.api

import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.IArchive
import dev.extframework.boot.monad.Tagged
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings

public interface GradleEntrypoint {
    public suspend fun configure(
        extension: ExtframeworkExtension,
        helper: Helper
    )

    public interface Helper {
        public val repository: ExtensionRepositorySettings

        public fun attachDependencies(
            partition: PartitionHandler<*>,
            classes: List<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>,
        )

        public suspend fun tweak(
            environment: ExtensionEnvironment
        )
    }

//    public fun tweak(
//        root: BuildEnvironment
//    )

//    /**
//     * Emit environments to configure under
//     */
//    public fun emit(
//        extension: ExtframeworkExtension
//    ) : Job<List<ExtensionEnvironment>>
}