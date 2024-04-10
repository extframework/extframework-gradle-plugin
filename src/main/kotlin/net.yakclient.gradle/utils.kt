package net.yakclient.gradle

import com.durganmcbroom.resources.openStream
import net.yakclient.archives.ArchiveReference
import org.gradle.api.Project
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

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
internal inline fun <reified K, reified V> Project.newMapProperty(default: () -> Map<K, V> = { HashMap()}): MapProperty<K, V> {
    return objects.mapProperty(K::class.java, V::class.java).convention(default())
}

internal fun ArchiveReference.write(path: Path) {
    JarOutputStream(FileOutputStream(path.toFile())).use { target ->
        reader.entries().forEach { e ->
            val entry = JarEntry(e.name)

            target.putNextEntry(entry)

            val eIn = e.resource.openStream()

            //Stolen from https://stackoverflow.com/questions/1281229/how-to-use-jaroutputstream-to-create-a-jar-file
            val buffer = ByteArray(1024)

            while (true) {
                val count: Int = eIn.read(buffer)
                if (count == -1) break

                target.write(buffer, 0, count)
            }

            target.closeEntry()
        }
    }
}