package com.kaolinmc.kiln.api

import com.kaolinmc.boot.archive.ArchiveNodeResolver
import com.kaolinmc.boot.archive.IArchive
import com.kaolinmc.boot.monad.Tagged
import com.kaolinmc.tooling.api.environment.ExtensionEnvironment
import com.kaolinmc.tooling.api.extension.artifact.ExtensionRepositorySettings

public interface GradleEntrypoint {
    public suspend fun configure(
        extension: KaolinExtension,
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