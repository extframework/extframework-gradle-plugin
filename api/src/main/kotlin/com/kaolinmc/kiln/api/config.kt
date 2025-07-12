package com.kaolinmc.kiln.api

public data class ExtensionConfig(
    val parents: Map<String, ExtensionParentAttrConfig> = emptyMap(),
    val repositories: ExtensionRepoConfig = ExtensionRepoConfig(),
)

public data class ExtensionRepoConfig(
    val central: Boolean = true,
    val local: Boolean = false,
    val custom: Map<String, String> = mapOf(),
) : Iterable<String> {
    override fun iterator(): Iterator<String> = buildList {
        if (central) add("central")
        if (local) add("local")
        addAll(custom.keys)
    }.iterator()
}

public data class ExtensionParentAttrConfig(
    val group: String?,
    val version: String?,
    val isProjectBuild: Boolean = false
)