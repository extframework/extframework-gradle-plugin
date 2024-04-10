package net.yakclient.gradle

import org.gradle.api.Action
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty


data class MutableExtensionRuntimeModel(
    val groupId: Property<String>,
    val name: Property<String>,
    val version: Property<String>,

    val packagingType: Property<String>, // Jar, War, Zip, etc...

    val extensionRepositories: SetProperty<Map<String, String>>,
    val extensions: SetProperty<Map<String, String>>,

    val partitions: SetProperty<MutableExtensionPartition>,
) {
//    fun mainPartition(configure: Action<MutableMainVersionPartition>) {
//        mainPartition.update { provider ->
//            provider.map {
//                configure.execute(it)
//
//                it
//            }
//        }
//    }
//
//    fun versionPartitions(configure: Action<MutableExtensionVersionPartition>) {
//        versionPartitions.update { p ->
//            p.map {
//                it.forEach {
//                    configure.execute(it)
//                }
//
//                it
//            }
//        }
//    }
}

interface PartitionRepositoryScope {
    fun add(repository: MutableExtensionRepository)
}

data class MutableExtensionRepository(
    val type: String,
    val settings: MutableMap<String, String>
)

data class MutableExtensionPartition(
    val type: String,
    val name: Property<String>,
    val path: Property<String>,
    val repositories: SetProperty<MutableExtensionRepository>,
    val dependencies: SetProperty<MutableMap<String, String>>,
    val options: MapProperty<String, String>
) {
    fun repositories(configure: Action<PartitionRepositoryScope>) {
        repositories.update { p ->
            val scope = object : PartitionRepositoryScope {
                val repositories = mutableSetOf<MutableExtensionRepository>()
                override fun add(repository: MutableExtensionRepository) {
                    repositories.add(repository)
                }
            }

            p.map {
                configure.execute(scope)
                it.addAll(scope.repositories)

                it
            }
        }
    }
}

//data class MutableMainVersionPartition(
//    override var name: Property<String>,
//    override var path: Property<String>,
//    override val repositories: SetProperty<MutableExtensionRepository>,
//    override val dependencies: SetProperty<MutableMap<String, String>>
//) : MutableExtensionPartition
//
//data class MutableExtensionVersionPartition(
//    override val name: Property<String>,
//    override val path: Property<String>,
//
//    val mappingNamespace: Property<String>,
//
//    val supportedVersions: SetProperty<String>,
//
//    override val repositories: SetProperty<MutableExtensionRepository>,
//    override val dependencies: SetProperty<MutableMap<String, String>>,
//
////    val mixins: MutableList<MutableExtensionMixin>,
//) : MutableExtensionPartition
//
//data class MutableExtensionTweakerPartition(
//    override var name: Property<String>,
//    override var path: Property<String>,
//
//    override val repositories: SetProperty<MutableExtensionRepository>,
//    override val dependencies: SetProperty<MutableMap<String, String>>,
//
//    val entrypoint: Property<String>
//) : MutableExtensionPartition