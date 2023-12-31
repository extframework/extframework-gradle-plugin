package net.yakclient.gradle

import com.durganmcbroom.artifact.resolver.open
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenLocalLayout
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import net.yakclient.common.util.resolve
import net.yakclient.common.util.resource.SafeResource
import net.yakclient.launchermeta.handler.copyToBlocking
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.IllegalStateException
import java.net.URI
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
    return YAKCLIENT_DIR resolve "client-$version.jar"
}

fun downloadClient(version: String, devMode: Boolean = false) {
    val outputPath = preDownloadClient(version)

    if (Files.exists(outputPath)) return

    val layout = if (devMode)
        SimpleMavenLocalLayout()
    else SimpleMavenDefaultLayout("http://maven.yakclient.net/snapshots", HashType.SHA1, true, true)

    val resourceOr = layout.resourceOf(
        "net.yakclient",
        "client",
        version,
        "all",
        "jar"
    )

    val resource = resourceOr.tapLeft { throw IllegalStateException(it.message) }.orNull()!!

    val safeResource = object : SafeResource {
        override val uri: URI = URI.create(resource.location)

        override fun open(): InputStream = resource.open()
    }

    safeResource copyToBlocking outputPath
}

fun preCacheExtension(project: Project, yak: YakClientExtension): Pair<ExtDescriptorArg, ExtRepoArg> {
    val repositoryDir = project.mavenLocal()
    val erm = yak.erm.get()
    val descriptor = ExtDescriptorArg(erm.groupId.get(), erm.name.get(), erm.version.get())

    return descriptor to ExtRepoArg(repositoryDir.toString(), "local")
}

//abstract class PublishDevExtension : Copy() {
//    @get:Input
//    abstract val toPublish : FileCollection
//
//    @get:OutputDirectory
//    abstract val output: Property<File>
//
//    @TaskAction
//    fun write(yak: YakClientExtension) {
//        toPublish.co
//        (project.tasks.getByName("jar").outputs.files ).forEach {
//            it.copyRecursively(output.get().resolve(it.name), overwrite = true)
//        }
//        (project.tasks.getByName("generateErm").outputs.files
//            .find { it.name=="erm.json" } ?: throw IllegalStateException("Couldnt find generated Extension Runtime model when creating mock repository for boot."))
//            .run {
//                copyRecursively(output.get() .resolve("${yak.erm.name}-${yak.erm.version}-erm.json"), overwrite = true)
//            }
//    }

abstract class LaunchMinecraft : JavaExec() {
    @Input
    val mcVersion: Property<String> = project.objects.property(String::class.java).convention(
        project.provider {
            project.findProperty("mcVersion") as String
        })
    @get:Input
    abstract val targetNamespace: Property<String>
}

internal fun Project.registerLaunchTask(yakclient: YakClientExtension, publishTask: Task) =
    tasks.register("launch", LaunchMinecraft::class.java) { exec ->
        val devMode = (findProperty("devMode") as? String)?.toBoolean() ?: false
        val environmentType = (findProperty("forceEnv") as? String) ?: "extension-dev"
        // If a mapping type is specified, use that. Otherwise, take the first matching one from a enabled partition

        exec.dependsOn(publishTask)

        val path = preDownloadClient(CLIENT_VERSION)
        val (desc, repo) = preCacheExtension(this, yakclient)

        exec.classpath(path)
        exec.mainClass.set(CLIENT_MAIN_CLASS)

        val args = mutableListOf<String>()
        if (devMode) {
            args.add("--devmode")
        }
        exec.args = args

        val wd = (YAKCLIENT_DIR resolve "wd").toAbsolutePath().toFile()
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
