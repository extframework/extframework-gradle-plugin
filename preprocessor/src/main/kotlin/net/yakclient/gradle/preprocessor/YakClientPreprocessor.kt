package net.yakclient.gradle.preprocessor

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.yakclient.client.api.annotation.Mixin
import net.yakclient.client.api.annotation.processor.InjectionMetadata
import net.yakclient.client.api.annotation.processor.InjectionOption
import net.yakclient.client.api.annotation.processor.InjectionPriorityOption
import net.yakclient.common.util.make
import java.nio.file.Path
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@SupportedAnnotationTypes(
    "net.yakclient.client.api.annotation.Mixin",
)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
class YakClientPreprocessor : AbstractProcessor() {
    private val mixins: MutableMap<String, MutableMixin> = HashMap()
    private lateinit var messager: Messager

    override fun init(processingEnv: ProcessingEnvironment) {
        messager = processingEnv.messager

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
        val r = if (roundEnv.processingOver()) {
            writeMetadata(); true
        } else processAnnotations(annotations, roundEnv)

        return r
    }

    private fun processAnnotations(
        annotations: MutableSet<out TypeElement>,
        roundEnv: RoundEnvironment
    ): Boolean {
        val mixinAnnotation =
            annotations.firstOrNull { it.qualifiedName.contentEquals("net.yakclient.client.api.annotation.Mixin") }
                ?: return false

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

        for (element in allViableElements(roundEnv)) {
            val (mirror, injectionMetadata) = element.annotationMirrors.firstNotNullOfOrNull { mirror ->
                mirror.annotationType.asElement().getAnnotation(InjectionMetadata::class.java)?.let { mirror to it }
            } ?: continue

            val annotationType = mirror.annotationType.asElement()
            if (annotationType !is TypeElement) {
                messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Error, annotation isn't a type element. This should never happen and is an internal error in annotation processing.",
                    annotationType
                )

                return false
            }

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

        return true
    }

    private fun writeMetadata() {
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val value = mapper.writeValueAsString(
            mixins.values
        )

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

    private companion object {
        const val OUTPUT_FILE_NAME = "mixin-annotations.json"
    }
}

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