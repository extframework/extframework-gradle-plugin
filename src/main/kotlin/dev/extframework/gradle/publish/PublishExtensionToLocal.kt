package dev.extframework.gradle.publish

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.extframework.gradle.ExtFrameworkExtension
import dev.extframework.gradle.util.ListPropertySerializer
import dev.extframework.gradle.util.MapPropertySerializer
import dev.extframework.gradle.util.ProviderSerializer
import dev.extframework.gradle.util.SetPropertySerializer
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import java.nio.file.Files
import kotlin.io.path.writeBytes

fun registerPublishExtensionToLocalTask(
    extension: ExtFrameworkExtension
) {
    val mapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(
            SimpleModule()
                .addSerializer(Provider::class.java, ProviderSerializer())
                .addSerializer(SetProperty::class.java, SetPropertySerializer())
                .addSerializer(MapProperty::class.java, MapPropertySerializer())
                .addSerializer(ListProperty::class.java, ListPropertySerializer())
        )

    val project = extension.project
    val tasks = project.tasks

    val publish = project.extensions.getByType(PublishingExtension::class.java)

    publish.publications.register("local-${project.name}", MavenPublication::class.java) {
        extension.afterFinalized { e ->
            it.artifactId = e.erm.get().name.get()
            e.partitions.forEach { partition ->
                it.artifact(tasks.named(partition.generatePrmTaskName)).classifier = partition.name
                it.artifact(tasks.named(partition.sourceSet.jarTaskName)).classifier = partition.name
            }
        }

        it.artifact(project.tasks.named("generateErm")).classifier = "erm"
    }
}