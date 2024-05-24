package net.yakclient.gradle

import net.yakclient.common.util.resolve
import net.yakclient.gradle.deobf.MinecraftMappings
import net.yakclient.gradle.tasks.GenerateMcSources
import net.yakclient.gradle.tasks.registerGenerateErmTask
import net.yakclient.gradle.tasks.registerLaunchTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.JvmEcosystemPlugin
import org.gradle.jvm.tasks.Jar
import java.net.URI
import java.nio.file.Path

internal const val CLIENT_VERSION = "1.1.1-SNAPSHOT"
internal const val CLIENT_MAIN_CLASS = "net.yakclient.client.MainKt"
internal val YAKCLIENT_DIR = Path.of(System.getProperty("user.home")) resolve ".yakclient"

class YakClientGradle : Plugin<Project> {
    override fun apply(project: Project) {
        MinecraftMappings.setup(project.layout.projectDirectory.file("mappings").asFile.toPath())

        project.plugins.apply(JvmEcosystemPlugin::class.java)
        val yakclient = project.extensions.create("yakclient", YakClientExtension::class.java, project)

        val generateErm = project.registerGenerateErmTask()

        project.tasks.named("jar", Jar::class.java) { jar ->
            jar.dependsOn(generateErm)
            jar.dependsOn(project.tasks.withType(GenerateMcSources::class.java))

            yakclient.partitions.configureEach { partition ->
                jar.from(partition.sourceSet.output) { copy ->
                    copy.into(partition.partition.path)
                }
            }
        }

        project.registerLaunchTask(yakclient, project.tasks.getByName("publishToMavenLocal"))
    }
}

fun RepositoryHandler.yakclient() {
    maven {
        it.isAllowInsecureProtocol = true
        it.url = URI.create("http://maven.yakclient.net/snapshots")
    }
}


