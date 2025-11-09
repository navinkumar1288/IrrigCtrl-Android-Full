// Minimal root build file — module-specific plugins are applied in app/build.gradle.kts

// Optional: common buildscript configuration or tasks can go here.
tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}