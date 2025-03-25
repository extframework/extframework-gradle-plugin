package dev.extframework.gradle

import BootLoggerFactory
import com.durganmcbroom.jobs.launch
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.gradle.DefaultExtframeworkExtension
import dev.extframework.gradle.api.ExtensionWorker
import dev.extframework.gradle.api.ExtframeworkExtension
import dev.extframework.gradle.api.GradleEntrypoint
import kotlinx.coroutines.runBlocking
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.FileOutputStream
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Stage 1: Load all parents from uber (including gradle partitions, and tweakers)
 * Stage 2: Apply parent tweakers (Ignoring any builds in this project)
 * Stage 3: Apply parent gradle partitions as separate gradle plugins
 * Stage 4: Run gradle configuration
 * Stage 6: Configuration end
 */

class ExtframeworkPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val worker = target.rootProject.extensions.findByType(ExtensionWorker::class.java)
            ?: target.rootProject.extensions.create("worker", DefaultExtensionWorker::class.java, target.rootProject)

        val initDPath = target.gradle.gradleHomeDir!!.toPath() resolve "init.d"
        val initScriptVersionPath = initDPath.resolve("extframework-dep-init-v.txt")
        val initScriptPath = initDPath resolve "dependencies.gradle.kts"

        val version = initScriptVersionPath.takeIf { it.exists() }?.readText()

        if (initScriptPath.make() || version != DEPENDENCIES_INIT_VERSION) {
            System.err.println("----------- Extension Framework -----------")
            System.err.println("No further action required, please rerun gradle to clear this message")
            System.err.println("This plugin requires a gradle init script present in the environment; this script has been installed, please retry.")

            this::class.java.getResourceAsStream("/dependencies.gradle.kts")!!.use { fin ->
                FileOutputStream(initScriptPath.toFile()).use { fout ->
                    fin.copyTo(fout)
                }
            }

            initScriptVersionPath.make()
            initScriptVersionPath.writeText(DEPENDENCIES_INIT_VERSION)

            worker.needsReload = true
        }

        if (worker.initialized) return

        for (project in target.rootProject.allprojects) {
            val path = project.layout.projectDirectory.asFile.toPath() resolve "extension.toml"
            if (!path.exists()) continue

            val extension =
                project.extensions.findByType(ExtframeworkExtension::class.java) ?: project.extensions.create(
                    "extension",
                    DefaultExtframeworkExtension::class.java,
                    project,
                    worker
                )

            extension.initialize()
        }

        if (worker.needsReload) {
            throw ReconfigurationException()
        }

        launch(BootLoggerFactory()) {
            runBlocking {
                worker.tweak()().merge()
            }
        }

        worker.initialized = true
    }

    companion object {
        const val EXTFRAMEWORK_CENTRAL = "https://repo.extframework.dev/registry"
        const val DEPENDENCIES_INIT_VERSION = "2"

    }
}