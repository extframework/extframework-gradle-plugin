package dev.extframework.gradle

import dev.extframework.gradle.api.*
import dev.extframework.gradle.api.ExtframeworkExtension.BuildCache
import dev.extframework.gradle.api.util.newListProperty
import dev.extframework.gradle.api.util.newMapProperty
import dev.extframework.gradle.api.util.newSetProperty
import dev.extframework.gradle.api.util.property
import dev.extframework.gradle.config.getTomlConfig
import dev.extframework.gradle.config.parseTomlConfig
import dev.extframework.tooling.api.ExtensionLoader
import dev.extframework.tooling.api.TOOLING_API_VERSION
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.extension.ExtensionNode
import dev.extframework.tooling.api.extension.partition.ExtensionPartitionContainer
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

internal open class DefaultExtframeworkExtension(
    override val project: Project,
    override val worker: EnvironmentInitializer,
) : ExtframeworkExtension {
    override val configuration = parseTomlConfig(getTomlConfig(project.layout.projectDirectory.asFile.toPath()))

    override val loader: ExtensionLoader = ExtensionLoader(
        worker.dataDir,
        this
    )

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
    override val defaultEnvironment = BuildEnvironment(
        loader.rootEnvironment.compose(
            "${project.path} root"
        ), this
    )
    override val environments: MutableList<BuildEnvironment> = arrayListOf(
        BuildEnvironment(
            defaultEnvironment, this
        )
    )
    override val build: BuildCache = BuildCache(
        ArrayList(),
        ArrayList(),
        ArrayList(),
        ArrayList(),
        ArrayList(),
    )
//    override val parentPlugins: MutableList<String> = ArrayList()
//    override val parents: MutableList<ExtensionNode> = ArrayList<ExtensionNode>()
    override val finalizationActions: MutableList<Action<ExtframeworkExtension>> = ArrayList()

    override fun finalizedBy(action: Action<ExtframeworkExtension>) {
        finalizationActions.add(action)
    }

    init {
        loader.environmentRegistry.register(defaultEnvironment.name, defaultEnvironment)
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
