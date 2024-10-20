package dev.extframework.gradle.tasks

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenLocalLayout
import com.durganmcbroom.jobs.launch
import com.durganmcbroom.resources.ResourceAlgorithm
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.gradle.*
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private fun getHomedir(): Path {
    return getMinecraftDir() resolve ".extframework"
}

private fun getMinecraftDir(): Path {
    val osName = System.getProperty("os.name").lowercase()
    val userHome = System.getProperty("user.home")

    return when {
        osName.contains("win") -> {
            val appData = System.getenv("APPDATA")?.let(Path::of) ?: Path.of(userHome, "AppData", "Roaming")
            appData resolve ".minecraft"
        }
        osName.contains("mac") -> Paths.get(userHome, "Library", "Application Support", "minecraft")
        else -> Paths.get(userHome, ".minecraft") // Assuming Linux/Unix-like
    }
}

fun preDownloadClient(version: String): Path {
    return getHomedir() resolve "client-$version.jar"
}

fun downloadClient(version: String, devMode: Boolean = false) {
    val outputPath = preDownloadClient(version)

    if (Files.exists(outputPath)) return
    val layout = if (devMode)
        SimpleMavenLocalLayout()
    else SimpleMavenDefaultLayout(
        "https://maven.extframework.dev/releases", ResourceAlgorithm.SHA1,
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

fun preCacheExtension(project: Project, ext: ExtFrameworkExtension): Pair<ArtifactMetadata.Descriptor, String> {
    val repositoryDir = project.mavenLocal()
    val erm = ext.erm.get()
    val descriptor = SimpleMavenDescriptor(erm.groupId.get(), erm.name.get(), erm.version.get(), null)

    return descriptor to repositoryDir.toString()
}

abstract class LaunchMinecraft : JavaExec() {
    @Input
    val mcVersion: Property<String> = project.objects.property(String::class.java)

    @get:Input
    abstract val targetNamespace: Property<String>
}

internal fun Project.registerLaunchTask(extframework: ExtFrameworkExtension, publishTask: Task) =
    tasks.register("launch", LaunchMinecraft::class.java) { exec ->
        val devMode = (findProperty("devMode") as? String)?.toBoolean() ?: false

        exec.dependsOn(publishTask)

        val path = preDownloadClient(CLIENT_VERSION)
        val (desc, repo) = preCacheExtension(this, extframework)

        exec.classpath(path)
        exec.mainClass.set(CLIENT_MAIN_CLASS)

        val wd = (getHomedir() resolve "wd").toAbsolutePath().toFile()
        wd.mkdirs()
        exec.workingDir = wd

        exec.doFirst {
            val mcVersion = exec.mcVersion.orNull ?: project.findProperty("mcVersion") as String
            val extensionPath = extframework.project.layout.buildDirectory.get().asFile.toPath() resolve "extension"
            exec.args = listOf(
                "-e", desc.name,
                "-r", "local@$repo",
                "--version=extframework-$mcVersion",
                "--mapping-namespace=${exec.targetNamespace.get()}",
                "--extension-dir=${extensionPath.toAbsolutePath()}"
            )
            if (Files.exists(extensionPath)) {
                extensionPath.toFile().deleteRecursively()
            }

            downloadClient(CLIENT_VERSION, devMode)
        }
    }
