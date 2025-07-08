gradle.projectsLoaded {
    allprojects {
        buildscript {
            dependencies {
                fileTree(
                    project.rootDir.resolve(".kaolin/buildpath").resolve(
                        project.path.replace(":", "_")
                    )
                ).forEach { jar ->
                    classpath(files(jar))
                }
            }
        }
    }
}