package net.yakclient.gradle.preprocessor

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.yakclient.client.api.annotation.Mixin
import net.yakclient.client.api.annotation.processor.InjectionMetadata
import net.yakclient.client.api.annotation.processor.InjectionOption
import net.yakclient.client.api.annotation.processor.InjectionPriorityOption
import net.yakclient.common.util.make
import java.io.File
import java.nio.file.Path
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.tools.StandardLocation

@SupportedAnnotationTypes(
    "net.yakclient.client.api.annotation.Mixin",
)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
class YakClientPreprocessor : AbstractProcessor() {
    class InjectionData(
        val type: String,
        val options: Map<String, Any>,
        val priority: Int = 0
    )

    class MutableMixin(
        val classname: String,
        val destination: String,
        val injections: MutableList<InjectionData>
    )

    private val mixins: MutableMap<String, MutableMixin> = HashMap()
    private var outputLocation: String? = null

    override fun init(processingEnv: ProcessingEnvironment) {
        outputLocation = processingEnv.options[OUTPUT_OPTION_NAME]

        super.init(processingEnv)
    }

    private fun getViableChildren(element: Element): List<Element> {
        val viable = element.annotationMirrors.any {
            it.annotationType.asElement().getAnnotation(InjectionMetadata::class.java) != null
        }

        return element.enclosedElements.flatMap(::getViableChildren) + (if (viable) listOf(element) else listOf())
    }

    private fun allViableElements(env: RoundEnvironment): List<Element> =
        env.rootElements.flatMap(::getViableChildren)

    override fun process(
        annotations: MutableSet<out TypeElement>,
        roundEnv: RoundEnvironment
    ): Boolean {
        if (roundEnv.processingOver()) writeMetadata()
        else processAnnotations(annotations, roundEnv)

        return true
    }

    private fun processAnnotations(
        annotations: MutableSet<out TypeElement>,
        roundEnv: RoundEnvironment
    ) {
        val mixinAnnotation =
            annotations.firstOrNull { it.qualifiedName.contentEquals("net.yakclient.client.api.annotation.Mixin") }
                ?: return

        roundEnv.getElementsAnnotatedWith(mixinAnnotation)
            .filterIsInstance<TypeElement>()
            .forEach {
                val mixin = it.getAnnotation(Mixin::class.java)

                val key = it.qualifiedName.toString()

                mixins[key] = MutableMixin(
                    key,
                    mixin.value,
                    ArrayList()
                )
            }

        allViableElements(roundEnv).forEach { element ->
            val (mirror, injectionMetadata) = element.annotationMirrors.firstNotNullOfOrNull { mirror ->
                mirror.annotationType.asElement().getAnnotation(InjectionMetadata::class.java)?.let { mirror to it }
            } ?: return@forEach


            val annotationType = mirror.annotationType.asElement()
            check(annotationType is TypeElement) { "Error, annotation isn't a type element. This should never happen and is an internal error in annotation processing." }

            fun getClassName(element: Element): String? {
                if (element.kind == ElementKind.CLASS) return (element as TypeElement).qualifiedName.toString()

                return element.enclosingElement?.let(::getClassName)
            }

            val className = getClassName(element)

            val options = mirror.elementValues.mapNotNull { (ex, value) ->
                val annotation = ex.getAnnotation(InjectionOption::class.java) ?: return@mapNotNull null

                annotation.value to value.value
            }.toMap()

            val priority = mirror.elementValues.firstNotNullOfOrNull { (ex, value) ->
                ex.getAnnotation(InjectionPriorityOption::class.java) ?: return@firstNotNullOfOrNull null

                value.value.toString().toInt()
            }

            mixins[className]?.injections?.add(
                InjectionData(
                    injectionMetadata.value,
                    options,
                    priority ?: 0
                )
            )
        }
    }

    private fun writeMetadata() {
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val value = mapper.writeValueAsString(
            mixins.values
        )

        outputLocation?.let { output ->
            val path = Path.of(output, OUTPUT_FILE_NAME)
            path.make()
            path.toFile().writeText(value)
        } ?: run {
            val filer = processingEnv.filer

            val createResource = filer.createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                OUTPUT_FILE_NAME
            )
            val writer = createResource.openWriter()
            writer.write(
                value
            )
            writer.close()
        }
    }

    private companion object {
        const val OUTPUT_OPTION_NAME = "yakclient.annotation.processor.output"
        const val OUTPUT_FILE_NAME = "mixin-annotations.json"
    }
}