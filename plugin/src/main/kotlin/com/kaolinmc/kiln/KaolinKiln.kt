package com.kaolinmc.kiln

import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.kaolinmc.common.util.make
import com.kaolinmc.common.util.resolve
import com.kaolinmc.kiln.api.EnvironmentInitializer
import com.kaolinmc.kiln.api.KaolinExtension
import kotlinx.coroutines.runBlocking
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.GradleVersion
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

class KaolinKiln : Plugin<Project> {
    override fun apply(target: Project): Unit = target.run {
        val minVersion = GradleVersion.version("8.14.2")

        if (GradleVersion.current() < minVersion) {
            throw GradleException("This plugin requires Gradle $minVersion or higher. Current version: ${GradleVersion.current()}")
        }

        runBlocking {
            val worker = target.rootProject.extensions.findByType(EnvironmentInitializer::class.java)
                ?: target.rootProject.extensions.create(
                    "worker",
                    DefaultExtensionInitializer::class.java,
                    target.rootProject
                )

            val initDPath = target.gradle.gradleHomeDir!!.toPath() resolve "init.d"
            val initScriptVersionPath = initDPath.resolve("kaolin-dep-init-v.txt")
            val initScriptPath = initDPath resolve "dependencies.gradle.kts"

            val version = initScriptVersionPath.takeIf { it.exists() }?.readText()

            if (initScriptPath.make() || version != DEPENDENCIES_INIT_VERSION) {
                System.err.println("----------- Extension Framework -----------")
                System.err.println("No further action required, please rerun gradle to clear this message")
                System.err.println("This plugin requires a gradle init script present in the environment; this script has been installed, please retry.")

                KaolinKiln::class.java.getResourceAsStream("/dependencies.gradle.kts")!!.use { fin ->
                    FileOutputStream(initScriptPath.toFile()).use { fout ->
                        fin.copyTo(fout)
                    }
                }

                initScriptVersionPath.make()
                initScriptVersionPath.writeText(DEPENDENCIES_INIT_VERSION)

                worker.needsReload = true
            }

            if (worker.bootstrapped) return@runBlocking

            for (project in target.rootProject.allprojects) {
                val path = project.layout.projectDirectory.asFile.toPath() resolve "extension.toml"
                if (!path.exists()) continue

                val extension =
                    project.extensions.findByType(KaolinExtension::class.java) ?: project.extensions.create(
                        "extension",
                        DefaultKaolinExtension::class.java,
                        project,
                        worker
                    )

                worker.bootstrap(extension)
            }

            if (worker.needsReload) {
                throw ReconfigurationException()
            }

            worker.bootstrapped = true

            target.gradle.projectsEvaluated {
                runBlocking {
                    for (project in target.rootProject.allprojects) {
                        val extension = project.extensions.findByType(
                            KaolinExtension::class.java
                        ) ?: continue

                        worker.configure(extension)
                    }

                    for (project in target.rootProject.allprojects) {
                        val extension = project.extensions.findByType(
                            KaolinExtension::class.java
                        ) ?: continue

                        for (action in extension.finalizationActions) {
                            action.execute(extension)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val KAOLIN_CENTRAL = "https://repo.kaolinmc.com/registry"
        const val DEPENDENCIES_INIT_VERSION = "5"
    }
}