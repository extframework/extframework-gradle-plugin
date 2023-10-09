package net.yakclient.gradle


data class ExtensionRuntimeModel(
    var groupId: String,
    var name: String,
    var version: String,

    var packagingType: String = "jar", // Jar, War, Zip, etc...

    var extensionClass: String = "",
    var mainPartition: MainVersionPartition = MainVersionPartition(
        "main",
        "",
        mutableListOf(),
        mutableListOf()
    ),

    val extensionRepositories: MutableList<Map<String, String>> = ArrayList(),
    val extensions: MutableList<Map<String, String>> = ArrayList(),

    val versionPartitions: MutableList<ExtensionVersionPartition> = ArrayList(),
    var tweakerPartition: ExtensionTweakerPartition? = null,

    var mappingType: String
)

data class ExtensionRepository(
    var type: String,
    val settings: MutableMap<String, String>
)

data class ExtensionMixin(
    var classname: String,
    var destination: String,
    val injections: MutableList<ExtensionInjection>
)

data class ExtensionInjection(
    var type: String,
    val options: MutableMap<String, String>,
    var priority: Int = 0
)


interface ExtensionPartition {
    var name: String
    var path: String
    val repositories: MutableList<ExtensionRepository>
    val dependencies: MutableList<MutableMap<String, String>>
}

data class MainVersionPartition(
    override var name: String,
    override var path: String,
    override val repositories: MutableList<ExtensionRepository>,
    override val dependencies: MutableList<MutableMap<String, String>>
) : ExtensionPartition

data class ExtensionVersionPartition(
    override var name: String,
    override var path: String,

    val supportedVersions: Set<String>,

    override val repositories: MutableList<ExtensionRepository>,
    override val dependencies: MutableList<MutableMap<String, String>>,

    val mixins: MutableList<ExtensionMixin>,
) : ExtensionPartition

data class ExtensionTweakerPartition(
    override var path: String,

    override val repositories: MutableList<ExtensionRepository>,
    override val dependencies: MutableList<MutableMap<String, String>>,

    var entrypoint: String
) : ExtensionPartition {
    override var name: String = "tweaker"
}