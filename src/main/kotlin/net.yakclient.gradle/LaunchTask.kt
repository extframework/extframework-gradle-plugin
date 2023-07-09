package net.yakclient.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import net.yakclient.common.util.openStream
import net.yakclient.common.util.parseHex
import net.yakclient.common.util.readInputStream
import net.yakclient.common.util.resolve
import net.yakclient.common.util.resource.ExternalResource
import net.yakclient.common.util.resource.LocalResource
import net.yakclient.launchermeta.handler.copyToBlocking
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import java.io.ByteArrayInputStream
import java.io.File
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

    val path = "net/yakclient/client/$version/client-$version-all.jar"
    val resource = if (devMode)
        LocalResource(URI.create("file://${project.mavenLocal()}/$path"))
    else
        ExternalResource(
                URI.create("http://maven.yakclient.net/snapshots$path"),
                String(URI.create("http://maven.yakclient.net/snapshots$path.sha1").openStream().readInputStream()).parseHex()
        )

    resource.copyToBlocking(outputPath)
}

fun preCacheExtension(project: Project,yak: YakClientExtension) : Pair<ExtDescriptorArg, ExtRepoArg> {
    val repositoryDir = project.mavenLocal()
    val descriptor = ExtDescriptorArg(yak.erm.groupId, yak.erm.name, yak.erm.version)

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

fun Project.registerLaunchTask(jar: TaskProvider<*>, yakclient: YakClientExtension) = tasks.register("launch", org.gradle.api.tasks.JavaExec::class.java) { exec ->
    val mcVersion: String by properties
    val devMode = (findProperty("devMode") as? String)?.toBoolean() ?: false

    exec.dependsOn(tasks.getByName("publishToMavenLocal"))

    val path = preDownloadClient("1.0-SNAPSHOT", project)
    val (desc, repo) = preCacheExtension(this,yakclient)

    exec.classpath(path)
    exec.mainClass.set("net.yakclient.client.MainKt")
    exec.args = listOf("-w", (buildDir.toPath() resolve "launch").toString(), "-v", mcVersion, "--accessToken", "")

    exec.setStandardInput(ObjectMapper().registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build()).writeValueAsBytes(
            listOf(ExtArg(desc, repo))
    ).let(::ByteArrayInputStream))

    exec.doFirst {
        downloadClient("1.0-SNAPSHOT", project, devMode)
    }
}