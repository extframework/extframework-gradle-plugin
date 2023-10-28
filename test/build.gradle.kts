plugins {
    kotlin("jvm") version "1.7.10"
    kotlin("kapt") version "1.8.10"
    id("maven-publish")
    id("net.yakclient") version "1.0.1"
}

group = "net.yakclient"
version = "1.0-SNAPSHOT"

tasks.launch {
    jvmArgs = listOf("-XstartOnFirstThread")
}

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        isAllowInsecureProtocol = true
        url = uri("http://maven.yakclient.net/snapshots")
    }
}
//
//@CacheableRule
//abstract class TargetJvmVersionRule @Inject constructor(val jvmVersion: Int) : ComponentMetadataRule {
//    @get:Inject abstract val objects: ObjectFactory
//
//    override fun execute(context: ComponentMetadataContext) {
//        println("Hey test this please??!")
//        throw RuntimeException("Please")
//        context.details.withVariant("compile") {
//            attributes {
//                attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, jvmVersion)
//                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
//            }
//        }
//    }
//}

dependencies {
    implementation("net.yakclient:client-api:1.0-SNAPSHOT")
    implementation(yakclient.tweakerPartition.map { it.sourceSet.output })
}

tasks.jar {
    archiveBaseName.set("yakgradle-ext-test")
}

yakclient {
    model {
        name = "yakgradle-ext-test"
        groupId = "net.yakclient.extensions"
        extensionClass = "net.yakclient.test.MyExtension"
    }

    mappingType = "mojang/deobfuscated"

    publications {
        groupId = "net.yakclient.extensions"
        artifactId = "yakgradle-ext-test-${name}"
    }


    tweakerPartition {
        entrypoint.set("net.yakclient.extensions.example.tweaker.TweakerEntry")

        this.dependencies {
            implementation("net.yakclient.components:ext-loader:1.0-SNAPSHOT")
            implementation("net.yakclient:boot:1.0-SNAPSHOT")
            implementation("net.yakclient:archives:1.1-SNAPSHOT")
            implementation("com.durganmcbroom:jobs:1.0-SNAPSHOT")
            implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT")
            implementation("com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT")
        }
    }

    partitions {
        val nineteen = create("nineteen_two") {
            this.dependencies {
                implementation(main)
                minecraft("1.19.2")
                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")

                implementation("net.yakclient:client-api:1.0-SNAPSHOT")
                "kaptNineteen_two"("net.yakclient:yakclient-preprocessor:1.0-SNAPSHOT")
            }

            supportedVersions.addAll(listOf("1.19.2", "1.18"))
        }

//        create("eighteen") {
//            this.dependencies {
//                implementation(nineteen)
//                minecraft("1.18")
//                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")
//
//                implementation("net.yakclient:client-api:1.0-SNAPSHOT")
//                "kaptEighteen"("net.yakclient:yakclient-preprocessor:1.0-SNAPSHOT")
//            }
//
//            supportedVersions.addAll(listOf("1.18"))
//        }
    }

    extension("net.yakclient.extensions:yakgradle-ext-test-2:1.0-SNAPSHOT")
}

publishing {
    publications {

        create<MavenPublication>("prod") {
            from(components["java"])
            artifact(tasks.generateErm) {
                classifier = "erm"
            }

            groupId = "net.yakclient.extensions"
            artifactId = "yakgradle-ext-test"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}