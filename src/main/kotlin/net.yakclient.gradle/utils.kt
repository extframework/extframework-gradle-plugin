package net.yakclient.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import java.nio.file.Path

fun <T> Property<T>.ifPresent(block: (T) -> Unit) {
    if (isPresent) block(get())
}

fun Project.mavenLocal(): Path = Path.of(repositories.mavenLocal().url)

internal inline fun <reified T> Project.property(default: () -> T? = { null }): Property<T> {
    return objects.property(T::class.java).convention(default())
}

internal inline fun <reified T> Project.newSetProperty(default: () -> Set<T> = { HashSet()}): SetProperty<T> {
    return objects.setProperty(T::class.java).convention(default())
}