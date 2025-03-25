package dev.extframework.gradle.api

import com.durganmcbroom.jobs.Job
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import org.gradle.api.Plugin
import org.gradle.api.Project

public interface GradleEntrypoint : Plugin<Project> {
    override fun apply(project: Project)

    public fun setup(environment: ExtensionEnvironment) : Job<Unit>
}