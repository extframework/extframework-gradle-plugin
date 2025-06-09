plugins {
    kotlin("jvm")
    `maven-publish`
    idea
}

group = "dev.extframework"
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
//    implementation(fileTree("/Users/durganmcbroom/IdeaProjects/extframework/yakclient-gradle/testing/library/fakeDir"))
    implementation(mapOf("name" to "test/dir/entrypoint-1.0-BETA"))
//    implementation(mapOf("name" to "entrypoint-1.0-BETA", "classifier" to "sources"))
//    implementation(name = "")
//    implementation(fileTree("fakeDir"))
//    implementation("test:entrypoint:1.0.1-BETA")
}