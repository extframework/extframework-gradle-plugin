package com.kaolinmc.kiln

import org.gradle.api.publish.maven.MavenPublication

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