allprojects {
    buildscript {
        dependencies {
            fileTree(project.rootDir.resolve(".extframework/buildpath").resolve(
                project.path.replace(":", "_")
            )).forEach { jar ->
                classpath(files(jar))
            }
        }
    }
}