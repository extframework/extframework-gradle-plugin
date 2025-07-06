package com.kaolinmc.kiln.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.kaolinmc.common.util.resolve
import com.kaolinmc.kiln.api.ExtensionConfig
import com.kaolinmc.kiln.api.ExtensionParentAttrConfig
import java.nio.file.Path

fun getTomlConfig(
    projectPath: Path
): String {
    return (projectPath resolve "extension.toml").toFile().readText()
}

fun parseTomlConfig(
    content: String,
): ExtensionConfig {
    return TomlMapper()
        .registerModule(KotlinModule.Builder().build())
        .addMixIn(ExtensionParentAttrConfig::class.java, ConfigAttrMixin::class.java)
        .readValue(content, ExtensionConfig::class.java)
}

private abstract class ConfigAttrMixin {
    @get:JsonProperty("use")
    abstract val repository : String
    @get:JsonProperty("isBuild")
    abstract val isProjectBuild : Boolean
}