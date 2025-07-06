package com.kaolinmc.kiln.api

public data class ExtensionConfig(
    val parents: Map<String, ExtensionParentAttrConfig> = emptyMap(),
    val repositories: ExtensionRepoConfig = ExtensionRepoConfig(),
)

public data class ExtensionRepoConfig(
    val central: Boolean = false,
    val local: Boolean = false,
    val custom: Map<String, String> = mapOf(),
)

public data class ExtensionParentAttrConfig(
    val group: String?,
    val version: String?,
    val repository : String = "central",
    val isProjectBuild : Boolean = false
)