package net.yakclient.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Property
import java.nio.file.Path

fun <T> Property<T>.ifPresent(block: (T) -> Unit) {
    if (isPresent) block(get())
}

fun Project.mavenLocal(): Path = Path.of(repositories.mavenLocal().url)
