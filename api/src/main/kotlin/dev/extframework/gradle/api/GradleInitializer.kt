package dev.extframework.gradle.api

import dev.extframework.tooling.api.environment.EnvironmentAttribute
import dev.extframework.tooling.api.environment.EnvironmentAttributeKey
import dev.extframework.tooling.api.extension.ExtensionNode
import org.gradle.api.Project

public interface GradleInitializer : EnvironmentAttribute {
    override val key: EnvironmentAttributeKey<*>
        get() = GradleInitializer

    public fun apply(nodes: List<ExtensionNode>, project: Project)

    public companion object : EnvironmentAttributeKey<GradleInitializer>
}