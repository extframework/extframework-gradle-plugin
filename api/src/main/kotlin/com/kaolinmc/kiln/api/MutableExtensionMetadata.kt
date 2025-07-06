package com.kaolinmc.kiln.api

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

public data class MutableExtensionMetadata(
    val name: Property<String>,
    val app: Property<String>,
    val developers: ListProperty<String>,
    val icon: Property<String?>,
    val description: Property<String>,
    val tags: ListProperty<String>,
)