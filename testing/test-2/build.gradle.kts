//import com.kaolinmc.core.main.main
//import com.kaolinmc.core.main.main
import com.kaolinmc.gradle.common.*
//import com.kaolinmc.minecraft.minecraft
//import com.kaolinmc.minecraft.task.LaunchMinecraft
//import com.kaolinmc.minecraft.MojangNamespaces
//import com.kaolinmc.minecraft.task.LaunchMinecraft

plugins {
    kotlin("jvm")
    id("maven-publish")
    id("kaolin.kiln")
    id("com.kaolinmc.common")
}

group = "com.kaolinmc.extension"
version = "1.0-BETA"

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = uri("https://repo.kaolinmc.com/registry")
    }
    kaolin()
}

//val launch by tasks.registering(LaunchMinecraft::class) {
//    mcVersion.set("1.21.4")
//    targetNamespace.set(MojangNamespaces.deobfuscated.identifier)
//    dependsOn(tasks.named("publishToMavenLocal"))
//    javaLauncher.set(javaToolchains.launcherFor {
//        languageVersion.set(JavaLanguageVersion.of(21))
//    })
//}

extension {
    model {
        attribute("reloadable", "false")
    }
    partitions {
//        main {
//            extensionClass = "com.kaolinmc.extension.test.Test2Entrypoint"
//            dependencies {
//            }
//        }
//        minecraft("some_version") {
//            mappings = MojangNamespaces.deobfuscated
//            entrypoint = "com.kaolinmc.extension.test2.Class"
//            dependencies {
//                minecraft("1.21.4")
//            }
//            supportVersions("1.21.4")
//        }
//        minecraft("second_version") {
//            mappings = MojangNamespaces.deobfuscated
//            entrypoint = "com.kaolinmc.extension.test2.Class"
//            dependencies {
//                minecraft("1.21")
//            }
//            supportVersions("1.21")
//        }
        tweaker {
            tweakerClass = "com.kaolinmc.extensions.example.test2.Tweaker2Entry"
            dependencies {
                implementation(toolingApi())
            }
        }
    }
}