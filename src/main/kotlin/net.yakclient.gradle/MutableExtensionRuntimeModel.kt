package net.yakclient.gradle


data class MutableExtensionRuntimeModel(
    var groupId: String,
    var name: String,
    var version: String,

    var packagingType: String = "jar", // Jar, War, Zip, etc...

    var extensionClass: String = "",
    val mainPartition: MutableMainVersionPartition = MutableMainVersionPartition(
        "main",
        "",
        mutableListOf(),
        mutableListOf()
    ),

    val extensionRepositories: MutableList<Map<String, String>> = ArrayList(),
    val extensions: MutableList<Map<String, String>> = ArrayList(),

    val versionPartitions: MutableList<MutableExtensionVersionPartition> = ArrayList(),
    var tweakerPartition: MutableExtensionTweakerPartition? = null,

    var mappingType: String
)

data class MutableExtensionRepository(
    var type: String,
    val settings: MutableMap<String, String>
)

data class MutableExtensionMixin(
    var classname: String,
    var destination: String,
    val injections: MutableList<MutableExtensionInjection>
)

data class MutableExtensionInjection(
    var type: String,
    val options: MutableMap<String, String>,
    var priority: Int = 0
)


interface MutableExtensionPartition {
    var name: String
    var path: String
    val repositories: MutableList<MutableExtensionRepository>
    val dependencies: MutableList<MutableMap<String, String>>
}

data class MutableMainVersionPartition(
    override var name: String,
    override var path: String,
    override val repositories: MutableList<MutableExtensionRepository>,
    override val dependencies: MutableList<MutableMap<String, String>>
) : MutableExtensionPartition

data class MutableExtensionVersionPartition(
    override var name: String,
    override var path: String,

    val supportedVersions: Set<String>,

    override val repositories: MutableList<MutableExtensionRepository>,
    override val dependencies: MutableList<MutableMap<String, String>>,

    val mixins: MutableList<MutableExtensionMixin>,
) : MutableExtensionPartition

data class MutableExtensionTweakerPartition(
    override var path: String,

    override val repositories: MutableList<MutableExtensionRepository>,
    override val dependencies: MutableList<MutableMap<String, String>>,

    var entrypoint: String
) : MutableExtensionPartition {
    override var name: String = "tweaker"
}