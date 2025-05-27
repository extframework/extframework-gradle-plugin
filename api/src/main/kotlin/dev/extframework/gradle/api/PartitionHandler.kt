package dev.extframework.gradle.api

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet

public abstract class PartitionHandler<T : PartitionDependencyHandler>(
    project: Project,
    public val model: MutablePartitionRuntimeModel,
    public val sourceSet: SourceSet,
    // A shorthand for executing configurations just as the configuration block of this partition ends.
    private val configure: (() -> Unit) -> Unit
) : Named {
    protected val extframework: ExtframeworkExtension = project.extensions.getByType(ExtframeworkExtension::class.java)

    public abstract val dependencies: T

    public open fun model(action: Action<MutablePartitionRuntimeModel>) {
        action.execute(model)
    }

    public open fun dependencies(action: Action<T>) {
        configure {
            action.execute(dependencies)
        }
    }

    override fun getName(): String {
        return sourceSet.name
    }
}