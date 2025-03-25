allprojects {
    buildscript {
        dependencies {
            fileTree(project.rootDir.resolve(".extframework/buildpath")).forEach { jar ->
                classpath(files(jar))
            }
        }
    }
}