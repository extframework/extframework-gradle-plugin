package dev.extframework.gradle

import BootLoggerFactory
import com.durganmcbroom.jobs.launch
import dev.extframework.gradle.api.ExtensionWorker
import dev.extframework.gradle.api.ExtframeworkExtension
import dev.extframework.gradle.api.MutableExtensionMetadata
import dev.extframework.gradle.api.MutableExtensionRuntimeModel
import dev.extframework.gradle.api.NamedDomainPartitionContainer
import dev.extframework.gradle.config.getTomlConfig
import dev.extframework.gradle.config.parseTomlConfig
import dev.extframework.gradle.api.util.newListProperty
import dev.extframework.gradle.api.util.newMapProperty
import dev.extframework.gradle.api.util.newSetProperty
import dev.extframework.gradle.api.util.property
import dev.extframework.gradle.util.setupProject
import dev.extframework.tooling.api.ExtensionLoader
import dev.extframework.tooling.api.TOOLING_API_VERSION
import kotlinx.coroutines.runBlocking
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

abstract class DefaultExtframeworkExtension(
    override val project: Project,
    override val worker: ExtensionWorker
) : ExtframeworkExtension {
    override val configuration = parseTomlConfig(getTomlConfig(project.layout.projectDirectory.asFile.toPath()))
    val loader: ExtensionLoader by worker::loader

    override val partitions = DefaultPartitionContainer(this)
    override val sourceSets: SourceSetContainer by lazy { project.extensions.getByType(SourceSetContainer::class.java) }

    override val model = MutableExtensionRuntimeModel(
        TOOLING_API_VERSION,
        project.provider {
            project.group as? String ?: throw Exception("No 'project.group' set!")
        },
        project.property {
            project.name
        },
        project.provider {
            project.version as? String ?: throw Exception("No 'project.version' set!")
        },
        project.newListProperty(),
        project.newSetProperty(),
        project.newSetProperty(),
        project.newMapProperty(),
    )
    override val metadata: MutableExtensionMetadata = MutableExtensionMetadata(
        project.property(),
        project.property(),
        project.newListProperty(),
        project.property(),
        project.property(),
        project.newListProperty()
    )

    init {
        // Possibly change to just after when this build has been evaluated?
        project.gradle.projectsEvaluated {
            launch(BootLoggerFactory()) {
                runBlocking {
                    worker.setupPartitions(this@DefaultExtframeworkExtension)().merge()
                }
            }
        }
    }

    private var initialized = false
    override fun initialize() {
        if (initialized) return
        initialized = true

        launch(BootLoggerFactory()) {
            runBlocking {
                worker.initialize(this@DefaultExtframeworkExtension)().merge()
            }
        }

        setupProject(project, this)
    }

    override fun partitions(action: Action<NamedDomainPartitionContainer>) {
        action.execute(partitions)
    }

    override fun model(action: Action<MutableExtensionRuntimeModel>) {
        action.execute(model)
    }

    override fun metadata(action: Action<MutableExtensionMetadata>) {
        action.execute(metadata)
    }
}
