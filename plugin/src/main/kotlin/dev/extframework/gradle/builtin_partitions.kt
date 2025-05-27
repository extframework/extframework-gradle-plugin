package dev.extframework.gradle

import dev.extframework.gradle.api.*
import dev.extframework.gradle.api.util.newListProperty
import dev.extframework.gradle.api.util.newMapProperty
import dev.extframework.gradle.api.util.newSetProperty
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.tasks.Jar

class DefaultTweakerPartitionHandler(
    project: Project,
    partition: MutablePartitionRuntimeModel,
    sourceSet: SourceSet, configure: (() -> Unit) -> Unit
) : TweakerPartitionHandler(project, partition, sourceSet, configure) {
    override val dependencies = PartitionDependencyHandler(
        project.dependencies, sourceSet
    ) {
        partition.dependencies.add(it)
    }

    override var tweakerClass: String
        get() {
            return model.options.getting("tweaker-class").get()
        }
        set(value) {
            model.options.put("tweaker-class", value)
        }
}

class DefaultGradlePartitionHandler(
    project: Project,
    partition: MutablePartitionRuntimeModel,
    sourceSet: SourceSet, configure: (() -> Unit) -> Unit
) : GradlePartitionHandler(project, partition, sourceSet, configure) {
    override val dependencies = PartitionDependencyHandler(
        project.dependencies, sourceSet
    ) {
        partition.dependencies.add(it)
    }

    override var entrypointClass: String
        get() {
            return model.options.getting("entrypoint").get()
        }
        set(value) {
            model.options.put("entrypoint", value)
        }
}

class DefaultPartitionContainer(
    extension: ExtframeworkExtension
) : NamedDomainPartitionContainer(extension.project.container(PartitionHandler::class.java), extension) {
    private val project by extension::project

    override fun <T : PartitionHandler<*>> doAdd(
        action: Action<T>,
        getHandler: (extraConfig: (() -> Unit) -> Unit) -> T
    ): T {
        val toConfigure = ArrayList<() -> Unit>()

        val handler = getHandler(toConfigure::add)

        add(handler)
        extension.model {
            it.partitions.add(
                handler.model
            )
        }

        project.tasks.maybeCreate(
            handler.sourceSet.jarTaskName,
            Jar::class.java
        )
        project.tasks.create(
            handler.sourceSet.sourcesJarTaskName,
            Jar::class.java
        ) {
            it.from(handler.sourceSet.allSource)
            it.archiveClassifier.set(handler.name + "-sources")
        }

        project.tasks
            .withType(Jar::class.java)
            .named(handler.sourceSet.jarTaskName).configure {
                it.from(handler.sourceSet.output)
                it.archiveClassifier.set(handler.name)
            }

        action.execute(handler)
        toConfigure.forEach { it() }

        return handler
    }

    override fun tweaker(action: Action<TweakerPartitionHandler>) {
        val partition = MutablePartitionRuntimeModel(
            "tweaker",
            "tweaker",
            project.newListProperty(),
            project.newSetProperty(),
            project.newMapProperty()
        )

        doAdd(action) {
            val sourceSet = extension.sourceSets.create("tweaker")

            DefaultTweakerPartitionHandler(
                project,
                partition,
                sourceSet,
                it
            )
        }
    }

    override fun gradle(action: Action<GradlePartitionHandler>) {
        val partition = MutablePartitionRuntimeModel(
            "gradle",
            "gradle",
            project.newListProperty(),
            project.newSetProperty(),
            project.newMapProperty()
        )

        doAdd(action) {
            val sourceSet = extension.sourceSets.create("gradle")

            DefaultGradlePartitionHandler(
                project,
                partition,
                sourceSet,
                it
            )
        }
    }
}