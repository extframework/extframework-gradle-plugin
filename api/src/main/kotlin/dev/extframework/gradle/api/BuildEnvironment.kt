package dev.extframework.gradle.api

import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor

public class BuildEnvironment(
    public val delegate: ExtensionEnvironment,
//    public val builtBy: ExtensionDescriptor,
    public val extension: ExtframeworkExtension
) : ExtensionEnvironment by delegate