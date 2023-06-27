package net.yakclient.gradle

import com.durganmcbroom.artifact.resolver.simple.maven.layout.mavenLocal
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

abstract class LaunchTask : DefaultTask() {
    @get:Input
    @get:Option(option = "mcVersion", description="Sets the minecraft version when launching.")
    abstract val mcVersion: Property<String>

    @get:Input
    @get:Option(option = "devMode", description="Puts this task into dev mode. You probably only want this if you are working on yakclient/this gradle plugin itself.")
    @get:Optional
    abstract val devMode: Property<Boolean>

    @get:Internal
    val bootCache = project.buildDir.resolve("boot-cache")
    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val yakclientDescriptor = SoftwareComponentDescriptor(
            "net.yakclient.components",
            "yak",
            "1.0-SNAPSHOT",
            null
    )

    init {
        devMode.convention(false)
    }

    fun preCache(yak: YakClientExtension): Pair<SoftwareComponentArtifactRequest, String> {

        val repositoryDir = bootCache.resolve("tmp").resolve("m2")
        val descriptor = SoftwareComponentDescriptor(yak.erm.groupId, yak.erm.name, yak.erm.version, null)

        val versionDir =
                repositoryDir.resolve(descriptor.group.replace('.', File.separatorChar)).resolve(descriptor.artifact)
                        .resolve(descriptor.version)
        (project.tasks.getByName("jar").outputs.files).forEach {
            it.copyRecursively(versionDir.resolve(it.name), overwrite = true)
        }
        (project.tasks.getByName("generateErm").outputs.files
                .find { it.name == "erm.json" }
                ?: throw IllegalStateException("Couldnt find generated Extension Runtime model when creating mock repository for boot."))
                .run {
                    copyRecursively(versionDir.resolve("${descriptor.artifact}-${descriptor.version}-erm.json"), overwrite = true)
                }

        return SoftwareComponentArtifactRequest(
                descriptor,
        ) to repositoryDir.toString()
    }

    private fun cacheComponent(client: HttpClient) {
        data class CacheComponentRequest(
                val repositoryType: String,
                val repository: String,
                val request: String
        )

        val body =  if (devMode.get()) CacheComponentRequest(
                "local", mavenLocal, "net.yakclient.components:yak:1.0-SNAPSHOT"
        ) else CacheComponentRequest(
                "default", "http://maven.yakclient.net/snapshots", "net.yakclient.components:yak:1.0-SNAPSHOT"
        )

        val httpRequest = HttpRequest.newBuilder(URI.create("http://localhost:5000/cache"))
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)))
                .header("Content-Type", "application/json")
                .build()

        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        assert(response.statusCode() == 200) { "Failed to cache yakclient component in daemon. The daemon might be down or the cache failed for some reason. Check if the daemon is running by pining localhost:5000 on your browser." }
    }

    private fun runComponent(client: HttpClient, request: SoftwareComponentArtifactRequest, location: String) {
        data class IsRunningResponse(
                val group: String,
                val artifact: String,
                val version: String,
                val isRunning: Boolean
        )

        data class YakExtensionDescriptor(
                val groupId: String,
                val artifactId: String,
                val version: String
        )

        data class YakExtensionRepository(
                val type: String,
                val location: String
        )

        data class YakExtensionConfiguration(
                val descriptor: YakExtensionDescriptor,
                val repository: YakExtensionRepository,
        )

        data class YakConfiguration(
                val mcVersion: String,
                val mcArgs: List<String>,
                val extensions: List<YakExtensionConfiguration>
        )


        val isRunningRequest = HttpRequest.newBuilder(URI.create("http://localhost:5000/isRunning?group=${yakclientDescriptor.group}&artifact=${yakclientDescriptor.artifact}&version=${yakclientDescriptor.version}"))
                .GET()
                .build()

        val isRunningResponse = client.send(isRunningRequest, HttpResponse.BodyHandlers.ofInputStream())
        val isRunning = mapper.readValue<IsRunningResponse>(isRunningResponse.body()).isRunning

        if (isRunning) {
            val stopRequest = HttpRequest.newBuilder(URI.create("http://localhost:5000/stop?group=${yakclientDescriptor.group}&artifact=${yakclientDescriptor.artifact}&version=${yakclientDescriptor.version}"))
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .header("Content-Type", "application/json")
                    .build()
            val stopResponse = client.send(stopRequest, HttpResponse.BodyHandlers.discarding())
            assert(stopResponse.statusCode() == 200) { "Failed to stop yakclient when trying to launch minecraft with testing component: '$request'" }
        }


        val startRequest = HttpRequest.newBuilder(URI.create("http://localhost:5000/start?group=${yakclientDescriptor.group}&artifact=${yakclientDescriptor.artifact}&version=${yakclientDescriptor.version}"))
                .PUT(
                        HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(
                                YakConfiguration(
                                        mcVersion.get(),
                                        listOf("--version", mcVersion.get(), "--accessToken", ""), // TODO eventually
                                        listOf(
                                                YakExtensionConfiguration(
                                                        YakExtensionDescriptor(
                                                                request.descriptor.group,
                                                                request.descriptor.artifact,
                                                                request.descriptor.version
                                                        ),
                                                        YakExtensionRepository(
                                                                "local",
                                                                location
                                                        )
                                                )
                                        )
                                )
                        ))
                )
                .header("Content-Type", "application/json")
                .build()
        val startResponse = client.send(startRequest, HttpResponse.BodyHandlers.discarding())
        assert(startResponse.statusCode() == 200) { "Failed to start the yakclient component with your extension. I promise these error messages will get better eventually." }
    }

    @TaskAction
    fun run() {
        val yakclient = project.extensions.getByName("yakclient") as YakClientExtension

        val (request, location) = preCache(yakclient)

        val client = HttpClient.newHttpClient()
        cacheComponent(client)
        runComponent(client, request, location)
    }
}

