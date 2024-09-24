package dev.extframework.gradle

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

data class MutableExtensionMetadata(
    val name: Property<String>,
    val developers: ListProperty<String>,
    val icon: Property<String?>,
    val description: Property<String>,
    val tags: ListProperty<String>,
) {
    val app: String = "minecraft"
}