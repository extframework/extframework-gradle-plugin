import com.mojang.math.Axis
import dev.extframework.mixin.api.Field
import dev.extframework.mixin.api.InjectCode
import dev.extframework.mixin.api.InjectionType
import dev.extframework.mixin.api.Invoke
import dev.extframework.mixin.api.Mixin
import dev.extframework.mixin.api.Select
import dev.extframework.mixin.api.Stack
import dev.extframework.mixin.api.replaceLast
import net.minecraft.client.gui.components.SplashRenderer
import net.minecraft.server.Bootstrap
import kotlin.math.sin

//package dev.extframework.extensions.test2
//
//import com.mojang.math.Axis
//import dev.extframework.mixin.api.*
//import net.minecraft.client.gui.components.SplashRenderer
//import net.minecraft.client.gui.screens.TitleScreen
//import net.minecraft.client.main.Main
//import net.minecraft.server.Bootstrap
//import org.joml.Quaternionf
//import kotlin.math.sin
//
//@Mixin(Main::class)
//object TestMixin {
//    @InjectCode(
//        "main"
//    )
//    @JvmStatic
//    fun init() {
//        println("THIS HAPPENED")
//    }
//}
//
//@Mixin(TitleScreen::class)
//class ThirdMixin {
//    @InjectCode(
//       "init",
//    )
//    fun asdf() {
//        Bootstrap.realStdoutPrintln("Yo here we are")
//    }
//}
//
//
//
//
//
@Mixin(SplashRenderer::class)
class FourthMixin {
    @InjectCode(
        "render",
        point = Select(
            field = Field(
                SplashRenderer::class,
                "splash"
            )
        ),
        ordinal = 1,
        type = InjectionType.AFTER
    )
    fun asdf(
        stack: Stack
    ) {
        stack.replaceLast("Durgan McBroom is the GOAT")
    }

    @InjectCode(
        "render",
        point = Select(
            invoke = Invoke(
                Axis::class,
                "rotationDegrees(F)"
            )
        ),
        type = InjectionType.BEFORE
    )
    fun changeOrientation(
        stack: Stack
    ) {
        throw Exception("THIS IS MORE IMPORTANT")
        Bootstrap.realStdoutPrintln(stack.iterator().asSequence().toList().toString())
//        stack.replaceLast(
//            (10f * sin(0.1 * i) + 20f).toFloat()
//        )
        i++
    }

//    @InjectCode(
//        "render",
//        point = Select(
//            invoke = Invoke(
//                Axis::class,
//                "rotationDegrees(F)"
//            )
//        ),
//        type = InjectionType.AFTER
//    )
//    fun changeOrientation(
//        stack: Stack
//    ) {
//        stack.replaceLast(Axis.ZP.rotationDegrees(
//            (10f * sin(0.1*i) + 20f).toFloat()
//        ))
//        i++
//    }

    companion object {
        @JvmField
        public var i = 0
    }
}