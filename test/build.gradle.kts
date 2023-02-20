import net.yakclient.common.util.resolve
import net.yakclient.gradle.YakClient

plugins {
    kotlin("jvm") version "1.7.10"
    kotlin("kapt") version "1.8.10"
    id("net.yakclient") version "1.0"
}

group = "net.yakclient"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    kapt("net.yakclient:yakclient-preprocessor:1.0-SNAPSHOT")
}

val generatedKotlinSources = project.buildDir.toPath().resolve("generated")
kapt {
    arguments {
        arg("yakclient.annotation.processor.output", generatedKotlinSources.toString())
    }
}

yakclient {
    model {
        extensionClass = ""
    }

    partitions {
        create("nineteen_two") {
            dependencies {
                minecraft("1.19.2")
            }

            supportedVersions.add("1.19.2")
        }

        create("eighteen") {
            dependencies {
                implementation(other("nineteen_two"))
                minecraft("1.18")
            }
        }
    }
    jar {
        dependsOn(tasks.compileKotlin)
        from(generatedKotlinSources resolve "injection-annotations.json") {
            rename {
                // Really dont need this check, but always a good idea
                if (it == "injection-annotations.json") "mixins.json" else it
            }
        }
    }
}

//tasks.jar {
//
//}
//tasks.jar.get().enabled = false

//configure<SourceSetContainer> {
//
//}

//configure<YakClient> {
//    partitions {
//        val partition = create("name") {
//            minecraft = buildMinecraft(
//                "version",
//                mappings
//            )
//
//            supportedVersions = listOf("")
//
//            depenencies {
//                implementation()
//            }
//        }
//    }
//}

