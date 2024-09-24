package dev.extframework.gradle

import com.durganmcbroom.artifact.resolver.simple.maven.layout.mavenLocal
import dev.extframework.internal.api.extension.ExtensionParent
import dev.extframework.internal.api.extension.ExtensionRepository
import dev.extframework.internal.api.extension.ExtensionRuntimeModel
import dev.extframework.internal.api.extension.PartitionModelReference
import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import java.io.Serializable

data class MutableExtensionRuntimeModel(
    val apiVersion: Int,
    val groupId: Property<String>,
    val name: Property<String>,
    val version: Property<String>,

    val repositories: ListProperty<Map<String, String>>,
    val parents: SetProperty<ExtensionParent>,

    val partitions: SetProperty<PartitionModelReference>
) {
    fun partitions(action: Action<PartitionModelReference>) {
        (partitions.orNull ?: emptySet()).forEach { partition ->
            action.execute(partition)
        }
    }

//    fun repositories(action: Action<PartitionRepositoryScope>) {
//        val scope = object : PartitionRepositoryScope {
//            val repositories = mutableSetOf<ExtensionRepository>()
//            override fun add(repository: ExtensionRepository) {
//                repositories.add(repository)
//            }
//        }
//
//        action.execute(scope)
//        repositories.addAll(scope.repositories.map { it.settings })
//    }
}


val ExtensionRuntimeModel.descriptor : ExtensionDescriptor
    get() = ExtensionDescriptor.parseDescriptor("$groupId:$name:$version")

data class MutablePartitionRuntimeModel(
    val type: String,

    val name: String,

    val repositories: ListProperty<ExtensionRepository>,
    val dependencies: SetProperty<Map<String, String>>,

    val options: MapProperty<String, String>
) {
//    fun repositories(action: Action<PartitionRepositoryScope>) {
//        val scope = object : PartitionRepositoryScope {
//            val repositories = mutableSetOf<ExtensionRepository>()
//            override fun add(repository: ExtensionRepository) {
//                repositories.add(repository)
//            }
//        }
//
//        action.execute(scope)
//        repositories.addAll(scope.repositories)
//    }
}


interface PartitionRepositoryScope {
    fun add(repository: ExtensionRepository)

    fun mavenLocal() {
        add(
            ExtensionRepository(
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
            ExtensionRepository(
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
            ExtensionRepository(
                "simple-maven",
                mutableMapOf(
                    "location" to location,
                    "type" to type
                )
            )
        )
    }

    fun extframework() {
        // TODO change when we get out of snapshot
        maven("https://maven.extframework.dev/snapshots")
    }
}
