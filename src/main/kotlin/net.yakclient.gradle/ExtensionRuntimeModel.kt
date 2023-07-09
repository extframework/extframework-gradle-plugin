package net.yakclient.gradle


data class ExtensionRuntimeModel(
    var groupId: String,
    var name: String,
    var version: String,

    var packagingType: String = "jar", // Jar, War, Zip, etc...

    var extensionClass: String = "",
    var mainPartition: String = "", // its name

    val extensionRepositories: MutableList<Map<String, String>> = ArrayList(),
    val extensions: MutableList<Map<String, String>> = ArrayList(),

    val versionPartitions: MutableList<ExtensionVersionPartition>,
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

data class ExtensionVersionPartition(
    var name: String,
    var path: String,

    val supportedVersions: MutableSet<String>,

    val repositories: MutableList<ExtensionRepository>,
    val dependencies: MutableList<Map<String, String>>,

    val mixins: MutableList<ExtensionMixin>
)
