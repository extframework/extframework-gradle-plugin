package com.kaolinmc.kiln

import com.kaolinmc.extloader.DefaultExtensionEnvironment
import com.kaolinmc.kiln.api.*
import com.kaolinmc.kiln.api.KaolinExtension.BuildCache
import com.kaolinmc.kiln.api.util.newListProperty
import com.kaolinmc.kiln.api.util.newMapProperty
import com.kaolinmc.kiln.api.util.newSetProperty
import com.kaolinmc.kiln.api.util.property
import com.kaolinmc.kiln.config.getTomlConfig
import com.kaolinmc.kiln.config.parseTomlConfig
import com.kaolinmc.tooling.api.TOOLING_API_VERSION
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

internal open class DefaultKaolinExtension(
    override val project: Project,
    override val worker: EnvironmentInitializer,
) : KaolinExtension {
    override val configuration = parseTomlConfig(getTomlConfig(project.layout.projectDirectory.asFile.toPath()))

    override val rootEnvironment: BuildEnvironment = BuildEnvironment(
        DefaultExtensionEnvironment("root"), this
    )

//    override val loader: ExtensionLoader = ExtensionLoader(
//        worker.dataDir,
//        this
//    )
//
//    override val defaultEnvironment = BuildEnvironment(
//        loader.rootEnvironment.compose(
//            "${project.path} root"
//        ), this
//    )
//
//    override val sourcesGraph: ArchiveGraph = SourcesArchiveGraph(worker.dataDir resolve "archives")
//    override val partitionSourceResolver: PartitionResolver = SourcePartitionResolver(
//        loader.extensionResolver.accessBridge,
//        loader.environmentRegistry,
//        defaultEnvironment.name
//    )

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
    override val environments: MutableList<BuildEnvironment> = mutableListOf(
        rootEnvironment
//        BuildEnvironment(
//            rootEnvironment, this
//        )
    )
    override val build: BuildCache = BuildCache(
        ArrayList(),
        ArrayList(),
        ArrayList(),
    )
    override val finalizationActions: MutableList<Action<KaolinExtension>> = ArrayList()

    override fun finalizedBy(action: Action<KaolinExtension>) {
        finalizationActions.add(action)
    }

    init {
//        loader.environmentRegistry.register(defaultEnvironment.name, defaultEnvironment)
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
