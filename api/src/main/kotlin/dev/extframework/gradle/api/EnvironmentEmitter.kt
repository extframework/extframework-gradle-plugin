package dev.extframework.gradle.api

import com.durganmcbroom.jobs.Job
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.environment.MutableObjectSetAttribute

public val environmentEmitters: MutableObjectSetAttribute.Key<EnvironmentEmitter> =
    MutableObjectSetAttribute.Key<EnvironmentEmitter>("environment-emitters")

public interface EnvironmentEmitter {
    public fun emit(
        extension: ExtframeworkExtension
    ): Job<List<ExtensionEnvironment>>
}