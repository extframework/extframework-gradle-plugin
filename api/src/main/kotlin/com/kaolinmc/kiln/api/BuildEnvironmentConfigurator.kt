//package com.kaolinmc.gradle.api
//
//import com.kaolinmc.boot.archive.ArchiveNodeResolver
//import com.kaolinmc.boot.archive.IArchive
//import com.kaolinmc.boot.monad.Tagged
//import com.kaolinmc.tooling.api.environment.MutableSetAttribute
//import com.kaolinmc.tooling.api.extension.artifact.ExtensionRepositorySettings
//
//public val environmentConfigurators: MutableSetAttribute.Key<BuildEnvironmentConfigurator> =
//    MutableSetAttribute.Key("environment-configurators")
//
//public fun interface BuildEnvironmentConfigurator {
//    /**
//     * Configures environments. This could include anything operation that is generally run in this
//     * environment, however an easy example is the loading of partitions.
//     */
//    public suspend fun configure(
//        environment: BuildEnvironment,
//        helper: Helper
//    )
//
//    public interface Helper {
//        public val repository: ExtensionRepositorySettings
//
//        public fun attachDependencies(
//            partition: PartitionHandler<*>,
//            classes: List<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>,
//        )
//
//        public suspend fun tweak()
//    }
//}