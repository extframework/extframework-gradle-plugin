package net.yakclient.gradle.preprocessor.test

import net.yakclient.gradle.preprocessor.YakClientPreprocessor
import org.junit.jupiter.api.Test
import java.io.File
import java.util.*
import java.util.spi.ToolProvider
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.tools.JavaCompiler
import javax.tools.StandardLocation

class MockTest {
    @Test
    fun testAnnotationProcessor() {
        // Create a test file that contains your annotations
        val testFile = checkNotNull(
            this::class.java.getResource("/TestCompilationClass.java")
        ).file.let(::File)


        val compiler = ServiceLoader.load(JavaCompiler::class.java).findFirst().get()
        val fileManager = compiler.getStandardFileManager(null, null, null)
        val javaFileObject = fileManager.getJavaFileObjects(testFile).first()
        val task = compiler.getTask(
            null,
            fileManager,
            null,
            listOf("-processor", YakClientPreprocessor::class.java.name, "-classpath", System.getProperty("java.class.path")),
            null,
            listOf(javaFileObject)
        )
        task.call()
        // Compile the test file using the mock annotation processor
    }


}