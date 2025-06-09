package dev.extframework.gradle.api

import com.durganmcbroom.jobs.async.AsyncJob
import dev.extframework.tooling.api.ExtensionLoader
import java.nio.file.Path

/**
 * Phases:
 *
 * Phase 1: Bootstrapping
 *    Occurs before anything else and performs operations that might require a reload. This includes
 *    adding additional dependencies to the buildscript, etc.
 *
 * Phase 2: Configuration
 *    Any operation needed to initialize the system / configure gradle. This includes caching partitions,
 *    registering dependencies with gradle, and building environments
 */
public interface EnvironmentInitializer {
    public var needsReload: Boolean
    public val dataDir: Path
    public var bootstrapped: Boolean

    public fun bootstrap(
        extension: ExtframeworkExtension,
    ) : AsyncJob<Unit>

    public fun configure(
        extension: ExtframeworkExtension,
    ) : AsyncJob<Unit>
}