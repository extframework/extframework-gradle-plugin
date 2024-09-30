package dev.extframework.gradle

import com.durganmcbroom.resources.openStream
import dev.extframework.archives.ArchiveReference
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.name

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
internal inline fun <reified T> Project.newListProperty(default: () -> List<T> = { ArrayList()}): ListProperty<T> {
    return objects.listProperty(T::class.java).convention(default())
}
internal inline fun <reified K, reified V> Project.newMapProperty(default: () -> Map<K, V> = { HashMap()}): MapProperty<K, V> {
    return objects.mapProperty(K::class.java, V::class.java).convention(default())
}

internal fun ArchiveReference.write(path: Path) {
    val temp = Files.createTempFile(path.name, "jar")

    JarOutputStream(FileOutputStream(temp.toFile())).use { target ->
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
    close()


    Files.copy(temp, path, StandardCopyOption.REPLACE_EXISTING)
}