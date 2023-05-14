import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
//    kapt("net.yakclient:yakclient-preprocessor:1.0-SNAPSHOT")
//    testImplementation(kotlin("test"))

}

yakclient {
    model {
        extensionClass = "net.yakclient.test.MyExtension"
    }

    partitions {
        val main by named {
            dependencies {

            }

            supportedVersions.addAll(listOf(""))
        }

        this.main = main

        val nineteen = named("nineteen_two") {
            dependencies {
                minecraft("1.19.2")
                implementation(main)
                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")

                implementation("net.yakclient:client-api:1.0-SNAPSHOT")
                "kaptNineteen_two"("net.yakclient:yakclient-preprocessor:1.0-SNAPSHOT")
            }

            supportedVersions.addAll(listOf("1.19.2", "1.18"))
        }

        named("eighteen") {
            dependencies {
                implementation(nineteen)
                implementation(main)
                minecraft("1.18")
                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")

                implementation("net.yakclient:client-api:1.0-SNAPSHOT")
                "kaptEighteen"("net.yakclient:yakclient-preprocessor:1.0-SNAPSHOT")

            }
            supportedVersions.addAll(listOf("1.18"))
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

