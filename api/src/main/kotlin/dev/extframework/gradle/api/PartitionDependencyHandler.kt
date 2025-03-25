package dev.extframework.gradle.api

import groovy.lang.Closure
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.tasks.SourceSet
import org.gradle.util.internal.GUtil

public open class PartitionDependencyHandler(
    protected val delegate: DependencyHandler,
    public val sourceSet: SourceSet,
    private val addDependency: (Dependency) -> Unit
) : DependencyHandler by delegate {
    override fun add(configurationName: String, dependencyNotation: Any): Dependency? {
        return this.add(configurationName, dependencyNotation, null)
    }

    override fun add(
        configurationName: String,
        dependencyNotation: Any,
        configureClosure: Closure<*>?
    ): Dependency? {
        val newNotation = when (dependencyNotation) {
            else -> dependencyNotation
        }

        val newConfig =
            ((if (sourceSet.name == SourceSet.MAIN_SOURCE_SET_NAME)
                ""
            else GUtil.toCamelCase(sourceSet.name)) + configurationName.replaceFirstChar {
                it.uppercase()
            }).replaceFirstChar { it.lowercase() }

        return delegate.add(newConfig, newNotation, configureClosure)?.also {
            addDependency(it)
        }
    }
}