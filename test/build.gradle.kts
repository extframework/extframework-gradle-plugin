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
    kapt("net.yakclient:yakclient-preprocessor:1.0-SNAPSHOT")
}

yakclient {
    model {
        extensionClass = ""
    }

    partitions {
        create("nineteen_two") {
            dependencies {
                minecraft("1.19.2")
                implementation("net.yakclient:client-api:1.0-SNAPSHOT")
                "kaptNineteen_two"("net.yakclient:yakclient-preprocessor:1.0-SNAPSHOT")
            }

            supportedVersions.add("1.19.2")
        }

        create("eighteen") {
            dependencies {
                implementation(other("nineteen_two"))
                minecraft("1.18")
                implementation("net.yakclient:client-api:1.0-SNAPSHOT")
                "kaptEighteen"("net.yakclient:yakclient-preprocessor:1.0-SNAPSHOT")

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

