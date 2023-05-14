package net.yakclient.gradle

import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import net.yakclient.boot.Boot
import net.yakclient.boot.BootContext
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.dependency.DependencyProviders
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class LaunchTask : DefaultTask() {
    @Internal
    val bootCache = project.buildDir.resolve("boot-cache")
    @Internal
    val boot = Boot(
        BootContext(DependencyProviders()),
        bootCache.resolve("maven").toString(),
        bootCache.resolve("component").toString()
    )

    fun preCache(yak: YakClientExtension): Pair<SoftwareComponentArtifactRequest, String> {
        val repositoryDir = bootCache.resolve("tmp").resolve("m2")
        val descriptor = SoftwareComponentDescriptor(yak.erm.groupId, yak.erm.name, yak.erm.version, null)

        val versionDir =
            repositoryDir.resolve(descriptor.group.replace('.', File.separatorChar)).resolve(descriptor.artifact)
                .resolve(descriptor.version)
        (project.tasks.getByName("jar").outputs.files ).forEach {
            it.copyRecursively(versionDir.resolve(it.name), overwrite = true)
        }
        (project.tasks.getByName("generateErm").outputs.files
            .find { it.name=="erm.json" } ?: throw IllegalStateException("Couldnt find generated Extension Runtime model when creating mock repository for boot."))
            .run {
                copyRecursively(versionDir.resolve("${descriptor.artifact}-${descriptor.version}-erm.json"), overwrite = true)
            }

        return SoftwareComponentArtifactRequest(
            descriptor,
        ) to repositoryDir.toString()
    }

    @TaskAction
    fun run() {
        val yakclient = project.extensions.getByName("yakclient") as YakClientExtension

        val (request, location) = preCache(yakclient)

        val yakclientDescriptor = SoftwareComponentDescriptor(
            "net.yakclient.components",
            "yak",
            "1.0-SNAPSHOT",
            null
        )
        boot.cache(
            SoftwareComponentArtifactRequest(yakclientDescriptor),
            SoftwareComponentRepositorySettings.default(
                "http://maven.yakclient.net/snapshots",
                preferredHash = HashType.SHA1
            ),HashMap()
        )
        //\"cache\"=\"/Users/durgan/IdeaProjects/yakclient/boot/cache\",\"extensions\"=\"net.yakclient.extensions:example-extension:1.0-SNAPSHOT->/Users/durgan/.m2/repository@local\""
        boot.configure(
            yakclientDescriptor,
            mapOf(
                "cache" to bootCache.resolve("extensions").toString(),
                "extensions" to "${request.descriptor}->$location@local"
            )
        )
        val rawVersion = "1.19.2"
        boot.configure(
            SoftwareComponentDescriptor(
                "net.yakclient.components",
                "minecraft-bootstrapper",
                "1.0-SNAPSHOT",
                null
            ),
            //\"version\"=\"1.19.2\",\"repository\"=\"/Users/durgan/.m2/repository\",\"repositoryType\"=\"LOCAL\",\"cache\"=\"/Users/durgan/IdeaProjects/yakclient/boot/cache\",\"providerVersionMappings\"=\"file:///Users/durgan/IdeaProjects/durganmcbroom/minecraft-bootstrapper/cache/version-mappings.json\",\"mcArgs\"=\"--version;1.19.2;--accessToken;\""
            mapOf(
                "version" to rawVersion,
                "repository" to "http://maven.yakclient.net/snapshots",
                "repositoryType" to "DEFAULT",
                "cache" to bootCache.resolve("mc-cache").toString(),
                "providerVersionMappings" to "http://maven.yakclient.net/public/mc-version-mappings.json",
                "mcArgs" to "--version;$rawVersion;--accessToken;"
            )
        )

//        boot.startAll(
//            listOf(SoftwareComponentArtifactRequest(yakclientDescriptor))
//        )
    }
}

