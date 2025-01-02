package dev.extframework.gradle.tasks

import BootLoggerFactory
import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenLocalLayout
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.launch
import com.durganmcbroom.resources.ResourceAlgorithm
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.resolve
import dev.extframework.gradle.CLIENT_MAIN_CLASS
import dev.extframework.gradle.CLIENT_VERSION
import dev.extframework.gradle.ExtFrameworkExtension
import dev.extframework.gradle.mavenLocal
import dev.extframework.gradle.minecraft.setupMinecraft
import dev.extframework.launchermeta.handler.DefaultMetadataProcessor
import kotlinx.coroutines.runBlocking
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively

private fun getHomedir(): Path {
    return getMinecraftDir() resolve ".extframework"
}

private fun getMinecraftDir(): Path {
    val osName = System.getProperty("os.name").lowercase()
    val userHome = System.getProperty("user.home")

    return when {
        osName.contains("win") -> {
            val appData = System.getenv("APPDATA")?.let(::Path) ?: Path(userHome, "AppData", "Roaming")
            appData resolve ".minecraft"
        }

        osName.contains("mac") -> Paths.get(userHome, "Library", "Application Support", "minecraft")
        else -> Paths.get(userHome, ".minecraft") // Assuming Linux/Unix-like
    }
}

fun preDownloadClient(version: String): Path {
    return getHomedir() resolve "client-$version.jar"
}

fun downloadClient(version: String, devMode: Boolean = false): AsyncJob<Path> = asyncJob() {
    val outputPath = preDownloadClient(version)

    if (Files.exists(outputPath)) return@asyncJob outputPath
    val layout = if (devMode)
        SimpleMavenLocalLayout()
    else SimpleMavenDefaultLayout(
        "https://maven.extframework.dev/releases", ResourceAlgorithm.SHA1,
        releasesEnabled = true,
        snapshotsEnabled = true,
    ) { _, _ ->
        true
    }

    val resource = layout.resourceOf(
        "dev.extframework",
        "client",
        version,
        "all",
        "jar"
    )

    resource copyTo outputPath
}

fun preCacheExtension(project: Project, ext: ExtFrameworkExtension): Pair<ArtifactMetadata.Descriptor, String> {
    val repositoryDir = project.mavenLocal()
    val erm = ext.erm.get()
    val descriptor = SimpleMavenDescriptor(erm.groupId.get(), erm.name.get(), erm.version.get(), null)

    return descriptor to repositoryDir.toString()
}

abstract class LaunchMinecraft : JavaExec() {
    @Input
    @Optional
    val mcVersion: Property<String> = project.objects.property(String::class.java)

    @get:Input
    abstract val targetNamespace: Property<String>

    @get:Input
    val minecraftArguments: MapProperty<String, String> = project.objects.mapProperty(String::class.java, String::class.java)
        .convention(mutableMapOf("auth_access_token" to ""))

    @TaskAction
    @ExperimentalPathApi
    override fun exec() = launch(BootLoggerFactory()) {
        runBlocking {
            val extframework = project.extensions.getByType(ExtFrameworkExtension::class.java)

            val mcDir = getMinecraftDir()
            val binDir = mcDir resolve "bin"
            binDir.deleteRecursively()

            val env = setupMinecraft(
                mcVersion.get(),
                mcDir
            )().merge()

            val devMode = (project.findProperty("devMode") as? String)?.toBoolean() ?: false

            val path = downloadClient(CLIENT_VERSION, devMode)().merge()
            val (desc, repo) = preCacheExtension(project, extframework)

            classpath(path)
            workingDir(mcDir.toFile())

            val mcVersion = mcVersion.orNull ?: project.findProperty("mcVersion") as String
            val extensionPath = extframework.project.layout.buildDirectory.get().asFile.toPath() resolve "extension"
            val paths: List<Path> = listOf(env.clientJar) + env.libraries
            args(
                "-e", desc.name,
                "-r", "local@$repo",
                "--version=$mcVersion",
                "--mapping-namespace=${targetNamespace.get()}",
                "--extension-dir=${extensionPath.toAbsolutePath()}",
                "--classpath=${paths.joinToString(";") { it.toString() }}",
                "--main-class=${env.mainClass}",
                "--game-jar=${env.clientJar}"
            )
            args(":")

            if (Files.exists(extensionPath)) {
                extensionPath.toFile().deleteRecursively()
            }

            val values = mapOf(
                "version" to mcVersion,
                "version_name" to mcVersion,
                "game_directory" to mcDir.toString(),
                "assets_root" to env.assets.toString(),
                "assets_index_name" to env.assetIndex,
                "natives_directory" to env.nativesDir.toString(),
                "classpath" to "~/nothing.jar"
            ) + minecraftArguments.get()

            val processor = DefaultMetadataProcessor()

            env.arguments.jvm.forEach { arg ->
                val arg = processor.formatArg(values, arg) ?: return@forEach

                jvmArgs(arg)
            }

            env.arguments.game
                .chunked(2)
                .forEach { (arg1, arg2) ->
                    val first: List<String> = processor.formatArg(values, arg1) ?: return@forEach
                    val second: List<String> = processor.formatArg(values, arg2) ?: return@forEach

                    args(first)
                    args(second)
                }

            super.exec()
        }
    }
}

internal fun Project.registerLaunchTask(extframework: ExtFrameworkExtension, publishTask: Task) =
    tasks.register("launch", LaunchMinecraft::class.java) { exec ->

        exec.dependsOn(publishTask)
        exec.mainClass.set(CLIENT_MAIN_CLASS)
    }
