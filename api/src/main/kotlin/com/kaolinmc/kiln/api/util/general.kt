package com.kaolinmc.kiln.api.util

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import java.nio.file.Path
import java.nio.file.Paths

public fun <T> Property<T>.ifPresent(block: (T) -> Unit) {
    if (isPresent) block(get())
}

public fun Project.mavenLocal(): Path = Paths.get(repositories.mavenLocal().url)

public inline fun <reified T> Project.property(default: () -> T? = { null }): Property<T> {
    return objects.property(T::class.java).convention(default())
}

public inline fun <reified T> Project.newSetProperty(default: () -> Set<T> = { HashSet()}): SetProperty<T> {
    return objects.setProperty(T::class.java).convention(default())
}
public inline fun <reified T> Project.newListProperty(default: () -> List<T> = { ArrayList()}): ListProperty<T> {
    return objects.listProperty(T::class.java).convention(default())
}
public inline fun <reified K, reified V> Project.newMapProperty(default: () -> Map<K, V> = { HashMap()}): MapProperty<K, V> {
    return objects.mapProperty(K::class.java, V::class.java).convention(default())
}