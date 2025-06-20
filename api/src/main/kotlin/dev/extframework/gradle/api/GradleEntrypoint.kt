package dev.extframework.gradle.api

import org.gradle.api.Plugin
import org.gradle.api.Project

public interface GradleEntrypoint : Plugin<Project> {
    override fun apply(
        project: Project
    )

    public fun tweak(
        root: BuildEnvironment
    )

//    /**
//     * Emit environments to configure under
//     */
//    public fun emit(
//        extension: ExtframeworkExtension
//    ) : Job<List<ExtensionEnvironment>>
}