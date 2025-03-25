package dev.extframework.gradle.api

import com.durganmcbroom.jobs.async.AsyncJob
import dev.extframework.tooling.api.ExtensionLoader
import org.gradle.api.Plugin
import java.nio.file.Path

public interface ExtensionWorker {
    public val loader: ExtensionLoader
    public var needsReload: Boolean
    public val plugins: List<Class<out Plugin<*>>>
    public val dataDir: Path

    public var initialized: Boolean

    public fun setupPartitions(
        extension: ExtframeworkExtension,
    ): AsyncJob<Unit>

    public fun initialize(
        extension: ExtframeworkExtension,
    ) : AsyncJob<Unit>

    public fun tweak() : AsyncJob<Unit>
}