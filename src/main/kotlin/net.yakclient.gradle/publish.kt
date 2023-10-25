package net.yakclient.gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskOutputs
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar

private inline fun <reified T : Task> TaskContainer.register(name: String, configure: Action<T>): TaskProvider<T> {
    return register(name, T::class.java, configure)
}
//
//internal fun Project.registerPublishingTasks() {
//    val publishExt = extensions.getByType(PublishingExtension::class.java)
//    val yakClientExtension = extensions.getByType(YakClientExtension::class.java)
//
//    project.afterEvaluate {
//        if (yakClientExtension.tweakerPartition.isPresent) {
//            val tweakerJar = tasks.register<Jar>("tweakerJar") { jar ->
//                jar.from(yakClientExtension.tweakerPartition.get().sourceSet.output)
//            }
//
//            publishExt.publications.register("tweaker", MavenPublication::class.java) {
//                it.setArtifacts(listOf(tweakerJar))
//
//                yakClientExtension.publicationConfiguration.get().execute(it)
//            }
//        }
//
//        val mainJar = tasks.register<Jar>("mainJar") { jar ->
//            jar.from(yakClientExtension.sourceSets.named("main").get().output)
//        }
//
//        publishExt.publications.register("main", MavenPublication::class.java) {
//            it.setArtifacts(listOf(mainJar))
//
//            yakClientExtension.publicationConfiguration.get().execute(it)
//        }
//
//        yakClientExtension.partitions.configureEach { v ->
//            val task = tasks.register<Jar>("${v.name}Jar") { jar ->
//                jar.from(v.sourceSet.output)
//            }
//
//            publishExt.publications.register(v.name, MavenPublication::class.java) {
//                it.setArtifacts(listOf(task))
//
//                yakClientExtension.publicationConfiguration.get().execute(it)
//            }
//        }
//
//        // Necessary, a hacky way to get around the fact that there seems to be a problem with the
//        // publish plugin pulling sources from incorrect artifacts.
//        tasks.withType(AbstractPublishToMaven::class.java) { p ->
//            tasks.withType(Jar::class.java).forEach {
//                p.dependsOn(it)
//            }
//        }
//    }
//}