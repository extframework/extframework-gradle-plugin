package com.kaolinmc.kiln.api

import com.kaolinmc.tooling.api.environment.ExtensionEnvironment

public class BuildEnvironment(
    public val delegate: ExtensionEnvironment,
//    public val builtBy: ExtensionDescriptor,
    public val extension: KaolinExtension
) : ExtensionEnvironment by delegate