//package dev.extframework.gradle.api
//
//import dev.extframework.boot.archive.ArchiveNodeResolver
//import dev.extframework.boot.archive.IArchive
//import dev.extframework.boot.monad.Tagged
//import dev.extframework.tooling.api.environment.MutableSetAttribute
//import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
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