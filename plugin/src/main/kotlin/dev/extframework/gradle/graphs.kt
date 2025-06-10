package dev.extframework.gradle

import dev.extframework.boot.API_VERSION
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.DefaultArchiveGraph
import dev.extframework.common.util.resolve
import java.nio.file.Path

class SourcesArchiveGraph(path: Path) : DefaultArchiveGraph(path) {
//    override fun registerResolver(resolver: ArchiveNodeResolver<*, *, *, *, *>) {
//    }

    override val path: Path = path resolve "sources" resolve "v$API_VERSION"
}

class ClassesArchiveGraph(path: Path) : DefaultArchiveGraph(path) {
    override val path: Path = path resolve "classes" resolve "v$API_VERSION"
}

// TODO composition in graph contexts. This is only important (and actually shouldn't be implemented as far as im
//    concerned in terms of anything else) for resolvers.
//class GraphEnvironmentContext(
//    val delegate: ArchiveGraph,
//) : ArchiveGraph by delegate {
//    val resolvers:
//}