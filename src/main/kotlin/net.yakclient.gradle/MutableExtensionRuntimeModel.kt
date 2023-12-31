package net.yakclient.gradle

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty


data class MutableExtensionRuntimeModel(
    val groupId: Property<String>,
    val name: Property<String>,
    val version: Property<String>,

    val packagingType: Property<String>, // Jar, War, Zip, etc...

    val extensionClass: Property<String>,
    val mainPartition: Property<MutableMainVersionPartition>,

//        MutableMainVersionPartition(
//        "main",
//        "",
//        mutableListOf(),
//        mutableListOf()
//    ),

    val extensionRepositories: SetProperty<Map<String, String>> ,
    val extensions: SetProperty<Map<String, String>>,

    val versionPartitions: SetProperty<MutableExtensionVersionPartition>,
    val tweakerPartition: Property<MutableExtensionTweakerPartition?>

//    = MutableExtensionTweakerPartition(
//        "META-INF/versioning/partitions/tweaker",
//        mutableListOf(),
//        mutableListOf(),
//        ""
//    ),
)

data class MutableExtensionRepository(
    val type: String,
    val settings: MutableMap<String, String>
)

//data class MutableExtensionMixin(
//    val classname: String,
//    val destination: String,
//    val injections: List<MutableExtensionInjection>
//)

//data class MutableExtensionInjection(
//    val type: String,
//    val options: MutableMap<String, String>,
//    val priority: Int = 0
//)

interface MutableExtensionPartition {
    val name: Property<String>
    val path: Property<String>
    val repositories: SetProperty<MutableExtensionRepository>
    val dependencies: SetProperty<MutableMap<String, String>>
}

data class MutableMainVersionPartition(
    override var name: Property<String>,
    override var path: Property<String>,
    override val repositories: SetProperty<MutableExtensionRepository>,
    override val dependencies: SetProperty<MutableMap<String, String>>
) : MutableExtensionPartition

data class MutableExtensionVersionPartition(
    override val name: Property<String>,
    override val path: Property<String>,

    val mappingNamespace: Property<String>,

    val supportedVersions: SetProperty<String>,

    override val repositories: SetProperty<MutableExtensionRepository>,
    override val dependencies: SetProperty<MutableMap<String, String>>,

//    val mixins: MutableList<MutableExtensionMixin>,
) : MutableExtensionPartition

data class MutableExtensionTweakerPartition(
    override var name: Property<String>,
    override var path: Property<String>,

    override val repositories: SetProperty<MutableExtensionRepository>,
    override val dependencies: SetProperty<MutableMap<String, String>>,

    val entrypoint: Property<String>
) : MutableExtensionPartition