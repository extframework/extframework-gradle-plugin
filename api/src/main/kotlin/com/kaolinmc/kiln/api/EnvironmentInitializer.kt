package com.kaolinmc.kiln.api

import com.kaolinmc.common.util.resolve
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
    public val dataDir: Path
    public val mock: MockPaths

    public var needsReload: Boolean
    public var bootstrapped: Boolean

    public val managed: Set<KaolinExtension>

    public suspend fun bootstrap(
        extension: KaolinExtension,
    )

    public suspend fun configure(
        extension: KaolinExtension,
    )

    public data class MockPaths(
        val path: Path,
        val repository: Path = path resolve ".m2",
        val archives: Path = path resolve "archives",
    )
}