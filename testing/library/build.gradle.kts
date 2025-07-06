plugins {
    kotlin("jvm")
    `maven-publish`
    idea
}

group = "com.kaolinmc"
version = "1"

repositories {
    mavenCentral()
    flatDir {
        dirs("fakeDir", "fakeDir2")
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

dependencies {
    implementation(mapOf("name" to "test/dir/entrypoint-1.0-BETA"))
}