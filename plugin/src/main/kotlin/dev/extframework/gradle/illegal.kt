package dev.extframework.gradle

import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal
import org.gradle.api.publish.maven.internal.tasks.MavenPomFileGenerator
import java.nio.file.Files

fun buildMavenPublication(
    publication: MavenPublication
) : ByteArray {
//    val temp = Files.createTempFile("pub", ".pom").toFile()

    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>${publication.groupId}</groupId>
          <artifactId>${publication.artifactId}</artifactId>
          <version>${publication.version}</version>
          <packaging>pom</packaging>
        </project>
    """.trimIndent().toByteArray()

//    // TODO Use of internal gradle APIs
//    var pom = (publication.pom  as MavenPomInternal)
//    val spec = MavenPomFileGenerator.generateSpec(
//        pom
//    )
//    spec.writeTo(temp)
//
//    return temp.readBytes()
}