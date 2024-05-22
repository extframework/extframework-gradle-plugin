package net.yakclient.gradle

import com.durganmcbroom.artifact.resolver.simple.maven.layout.mavenLocal
import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import java.io.Serializable


data class MutableExtensionRuntimeModel(
    val groupId: Property<String>,
    val name: Property<String>,
    val version: Property<String>,

    val packagingType: Property<String>, // Jar, War, Zip, etc...

    val extensionRepositories: ListProperty<Map<String, String>>,
    val extensions: SetProperty<Map<String, String>>,

    val partitions: SetProperty<MutableExtensionPartition>,
) {
    fun partitions(action: Action<MutableExtensionPartition>) {
        (partitions.orNull ?: emptySet()).forEach { partition ->
            action.execute(partition)
        }
    }

    fun extensionRepositories(action: Action<PartitionRepositoryScope>) {
        val scope = object : PartitionRepositoryScope {
            val repositories = mutableSetOf<MutableExtensionRepository>()
            override fun add(repository: MutableExtensionRepository) {
                repositories.add(repository)
            }
        }

        action.execute(scope)
        extensionRepositories.addAll(scope.repositories.map { it.settings })
    }
}

interface PartitionRepositoryScope {
    fun add(repository: MutableExtensionRepository)

    fun mavenLocal() {
        add(
            MutableExtensionRepository(
                "simple-maven",
                mutableMapOf(
                    "location" to mavenLocal,
                    "type" to "local"
                )
            )
        )
    }

    fun mavenCentral() {
        add(
            MutableExtensionRepository(
                "simple-maven",
                mutableMapOf(
                    "location" to "https://repo.maven.apache.org/maven2/",
                    "type" to "default"
                )
            )
        )
    }

    fun maven(
        location: String,
        type: String = "default"
    ) {
        add(
            MutableExtensionRepository(
                "simple-maven",
                mutableMapOf(
                    "location" to location,
                    "type" to type
                )
            )
        )
    }

    fun yakclient() {
        // TODO change when we get out of snapshot
        maven("http://maven.yakclient.net/snapshots")
    }
}

data class MutableExtensionRepository(
    val type: String,
    val settings: MutableMap<String, String>
)

data class MutableExtensionPartition(
    val type: String,
    val name: Property<String>,
    val path: Property<String>,
    val repositories: ListProperty<MutableExtensionRepository>,
    val dependencies: SetProperty<MutableMap<String, String>>,
    val options: MapProperty<String, String>
) : Serializable {
    fun repositories(action: Action<PartitionRepositoryScope>) {
        val scope = object : PartitionRepositoryScope {
            val repositories = mutableSetOf<MutableExtensionRepository>()
            override fun add(repository: MutableExtensionRepository) {
                repositories.add(repository)
            }
        }

        action.execute(scope)
        repositories.addAll(scope.repositories)
    }
}