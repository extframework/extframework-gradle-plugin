package dev.extframework.gradle.util

import dev.extframework.common.util.resolve
import java.nio.file.Path

internal fun Path.removePrefix(path: Path): Path {
    if (!startsWith(path)) return this

    var output: Path? = null

    for (it in this) {
        if (output != null) {
            output = output resolve it
        } else if (!path.contains(it)) {
            output = it
        }
    }

    return output!!
}