package com.kaolinmc.kiln.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DatabindException
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.kaolinmc.common.util.resolve
import com.kaolinmc.kiln.GradleExceptions
import com.kaolinmc.kiln.api.ExtensionConfig
import com.kaolinmc.kiln.api.ExtensionParentAttrConfig
import com.kaolinmc.tooling.api.exception.StructuredException
import java.nio.file.Path

fun getTomlConfig(
    projectPath: Path
): String {
    return (projectPath resolve "extension.toml").toFile().readText()
}

fun parseTomlConfig(
    content: String,
): ExtensionConfig {
    val mapper = TomlMapper()
        .registerModule(KotlinModule.Builder().build())
        .addMixIn(ExtensionParentAttrConfig::class.java, ConfigAttrMixin::class.java)

    try {
        return mapper
            .readValue(content, ExtensionConfig::class.java)
    } catch (e: DatabindException) {
        throw StructuredException(
            GradleExceptions.InvalidTomlConfiguration,
            description = "Invalid 'extension.toml' file declared.",
            cause = e
        ) {
            e.message asContext "Parsing exception"
        }
    }
}

private abstract class ConfigAttrMixin {
    @get:JsonProperty("use")
    abstract val repository : String
    @get:JsonProperty("isBuild")
    abstract val isProjectBuild : Boolean
}