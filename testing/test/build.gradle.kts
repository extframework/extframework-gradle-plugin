import com.kaolinmc.gradle.common.*

plugins {
    kotlin("jvm")

    id("maven-publish")
    id("kaolin.kiln")
    id("com.kaolinmc.common")
}

group = "com.kaolinmc.extension"
version = "1.0-BETA"

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://repo.kaolinmc.com/registry")
    }
    kaolin()
}

extension {
    partitions {
        tweaker {
            tweakerClass = "com.kaolinmc.extensions.test2.TweakerTest2"
            dependencies {
//               implementation(toolingApi())
            }
        }
        gradle {

        }
    }
}

dependencies {
}