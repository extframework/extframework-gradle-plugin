package net.yakclient.gradle

import com.durganmcbroom.artifact.resolver.open
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenLocalLayout
import com.fasterxml.jackson.databind.ObjectMapper
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.common.util.resource.SafeResource
import net.yakclient.launchermeta.handler.copyToBlocking
import org.gradle.api.Project
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
fun preDownloadClient(version: String, project: Project) : Path {
    return project.buildDir.toPath() resolve "launch" resolve "client-$version.jar"

}
fun downloadClient(version: String, project: Project, devMode: Boolean = false) {
    val outputPath = preDownloadClient(version, project)

    if (Files.exists(outputPath)) return

    val layout = if (devMode)
        SimpleMavenLocalLayout()
    else SimpleMavenDefaultLayout("http://maven.yakclient.net/snapshots", HashType.SHA1, true, true)

    val resourceOr = layout.resourceOf(
        "net.yakclient",
        "client",
        "1.0-SNAPSHOT",
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

fun preCacheExtension(project: Project,yak: YakClientExtension) : Pair<ExtDescriptorArg, ExtRepoArg> {
    val repositoryDir = project.mavenLocal()
    val erm = yak.erm
    val descriptor = ExtDescriptorArg(erm.groupId, erm.name, erm.version)

    return descriptor to ExtRepoArg(repositoryDir.toString(), "local")
}

//fun cacheExtension(project: Project, yak: YakClientExtension) {
//    val (descriptor, repo) = preCacheExtension(project,yak)
//    val repositoryDir = Path.of(repo.location)
//
//    val versionDir =
//            repositoryDir.resolve(descriptor.groupId.replace('.', File.separatorChar)).resolve(descriptor.artifactId)
//                    .resolve(descriptor.version)
//    (project.tasks.getByName("jar").outputs.files).forEach {
//        it.copyRecursively(versionDir.resolve(it.name).toFile(), overwrite = true)
//    }
//    (project.tasks.getByName("generateErm").outputs.files
//            .find { it.name == "erm.json" }
//            ?: throw IllegalStateException("Couldnt find generated Extension Runtime model when creating mock repository for boot."))
//            .run {
//                copyRecursively(versionDir.resolve("${descriptor.artifactId}-${descriptor.version}-erm.json").toFile(), overwrite = true)
//            }
//}

fun Project.registerLaunchTask(yakclient: YakClientExtension) = tasks.register("launch", org.gradle.api.tasks.JavaExec::class.java) { exec ->
    val mcVersion: String by properties
    val devMode = (findProperty("devMode") as? String)?.toBoolean() ?: false

    exec.dependsOn(tasks.getByName("publishToMavenLocal"))

    val path = preDownloadClient("1.0-SNAPSHOT", project)
    val (desc, repo) = preCacheExtension(this,yakclient)

    exec.classpath(path)
    exec.mainClass.set("net.yakclient.client.MainKt")
    val launchPath = buildDir.toPath() resolve "launch"
    val args = mutableListOf("-w", launchPath.toString(), "-v", mcVersion, "--accessToken", "")
    if (devMode) {
        args.add("--devmode")
    }
    exec.args = args

    val wd = (launchPath resolve "wd").toAbsolutePath().toFile()
    wd.mkdirs()
    exec.workingDir = wd

    exec.setStandardInput(ObjectMapper().registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build()).writeValueAsBytes(
            listOf(ExtArg(desc, repo))
    ).let(::ByteArrayInputStream))

    exec.doFirst {
        downloadClient("1.0-SNAPSHOT", project, devMode)
    }
}