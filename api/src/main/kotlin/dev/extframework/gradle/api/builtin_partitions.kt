package dev.extframework.gradle.api

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet

public abstract class TweakerPartitionHandler(
    project: Project,
    model: MutablePartitionRuntimeModel, sourceSet: SourceSet,
    configure: (() -> Unit) -> Unit
) : PartitionHandler<PartitionDependencyHandler>(project, model, sourceSet, configure) {
    public abstract var tweakerClass: String
}

public abstract class GradlePartitionHandler(
    project: Project,
    model: MutablePartitionRuntimeModel, sourceSet: SourceSet,
    configure: (() -> Unit) -> Unit
) : PartitionHandler<PartitionDependencyHandler>(project, model, sourceSet, configure) {
    public abstract var entrypointClass: String

}