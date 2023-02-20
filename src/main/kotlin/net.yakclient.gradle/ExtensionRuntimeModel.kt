package net.yakclient.gradle


// TODO make jackson parsing return a more readable yak wrapped error.
// Represents the YakClient ERM (or Extension runtime model)
data class ExtensionRuntimeModel(
    var groupId: String,
    var name: String,
    var version: String,

    var packagingType: String? = null, // Jar, War, Zip, etc...

    var extensionClass: String? = null,

    val dependencyRepositories: MutableList<ErmRepository> = ArrayList(),
    val dependencies: MutableList<Map<String, String>> = ArrayList(),

    val extensionRepositories: MutableList<Map<String, String>> = ArrayList(),
    val extensions: MutableList<Map<String, String>> = ArrayList(),

    val mixins : MutableSet<ExtensionMixin> = HashSet(),

    val versioningPartitions: MutableMap<String, List<String>> // Versions to partition
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

data class ErmRepository(
    var type: String,
    val settings: MutableMap<String, String>
)