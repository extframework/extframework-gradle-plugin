package dev.extframework.gradle.api

import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.environment.MutableObjectSetAttribute

public val environmentEmitters: MutableObjectSetAttribute.Key<EnvironmentEmitter> =
    MutableObjectSetAttribute.Key<EnvironmentEmitter>("environment-emitters")

public interface EnvironmentEmitter {
    public fun emit(
        extension: ExtframeworkExtension
    ) : List<ExtensionEnvironment>
}