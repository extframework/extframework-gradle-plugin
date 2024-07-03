package dev.extframework.gradle.tasks

import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenLocalLayout
import com.durganmcbroom.jobs.launch
import com.durganmcbroom.resources.ResourceAlgorithm
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.resolve
import dev.extframework.gradle.*
import dev.extframework.gradle.CLIENT_MAIN_CLASS
import dev.extframework.gradle.CLIENT_VERSION
import dev.extframework.gradle.HOME_DIR
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path

data class ExtRepoArg(
    val location: String,
    val type: String
)

data class ExtDescriptorArg(
    val groupId: String,
    val artifactId: String,
    val version: String
)

data class ExtArg(
    val descriptor: ExtDescriptorArg,
    val repository: ExtRepoArg
)

public enum class ExtLoaderEnvironmentType {
    PROD,
    EXT_DEV,
    INTERNAL_DEV
}

public data class ExtLoaderDevEnvironment(
    val extension: ExtArg,
    val mappingType: String
)

data class ExtLoaderEnvironment(
//    @JsonProperty("")
    val type: String,
    val context: ExtLoaderDevEnvironment
)

data class ExtLoaderArgs(
    @JsonProperty("minecraft-version")
    val mcVersion: String,
    @JsonProperty("minecraft-args")
    val mcArgs: List<String>,
    val environment: ExtLoaderEnvironment
)

fun preDownloadClient(version: String): Path {
    return HOME_DIR resolve "client-$version.jar"
}

fun downloadClient(version: String, devMode: Boolean = false) {
    val outputPath = preDownloadClient(version)

    if (Files.exists(outputPath)) return
    val layout = if (devMode)
        SimpleMavenLocalLayout()
    else SimpleMavenDefaultLayout("https://maven.extframework.dev/snapshots", ResourceAlgorithm.SHA1,
        releasesEnabled = true,
        snapshotsEnabled = true,
        requireResourceVerification = true
    )

    launch {
        val resource = layout.resourceOf(
            "dev.extframework",
            "client",
            version,
            "all",
            "jar"
        )().merge()

        resource copyTo outputPath
    }
}

fun preCacheExtension(project: Project, ext: ExtFrameworkExtension): Pair<ExtDescriptorArg, ExtRepoArg> {
    val repositoryDir = project.mavenLocal()
    val erm = ext.erm.get()
    val descriptor = ExtDescriptorArg(erm.groupId.get(), erm.name.get(), erm.version.get())

    return descriptor to ExtRepoArg(repositoryDir.toString(), "local")
}

abstract class LaunchMinecraft : JavaExec() {
    @Input
    val mcVersion: Property<String> = project.objects.property(String::class.java).convention(
        project.provider {
            project.findProperty("mcVersion") as String
        })

    @get:Input
    abstract val targetNamespace: Property<String>
}

internal fun Project.registerLaunchTask(extframework: ExtFrameworkExtension, publishTask: Task) =
    tasks.register("launch", LaunchMinecraft::class.java) { exec ->
        val devMode = (findProperty("devMode") as? String)?.toBoolean() ?: false
        val environmentType = (findProperty("forceEnv") as? String) ?: "extension-dev"
        // If a mapping type is specified, use that. Otherwise, take the first matching one from a enabled partition

        exec.dependsOn(publishTask)

        val path = preDownloadClient(CLIENT_VERSION)
        val (desc, repo) = preCacheExtension(this, extframework)

        exec.classpath(path)
        exec.mainClass.set(CLIENT_MAIN_CLASS)

        val args = mutableListOf<String>()
        if (devMode) {
            args.add("--devmode")
        }
        exec.args = args

        val wd = (HOME_DIR resolve "wd").toAbsolutePath().toFile()
        wd.mkdirs()
        exec.workingDir = wd

        exec.doFirst {
            exec.setStandardInput(
                ObjectMapper().registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
                    .writeValueAsBytes(
                        ExtLoaderArgs(
                            exec.mcVersion.get(),
                            listOf("--accessToken", ""),
                            ExtLoaderEnvironment(
                                environmentType,
                                ExtLoaderDevEnvironment(
                                    ExtArg(desc, repo),
                                    exec.targetNamespace.get()
                                )
                            )
                        )
                    ).let(::ByteArrayInputStream)
            )
            downloadClient(CLIENT_VERSION, devMode)
        }
    }