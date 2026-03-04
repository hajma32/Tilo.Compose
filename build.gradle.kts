plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    // code quality plugins (available for subprojects)
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

// Apply code quality plugins to subprojects where applicable
subprojects {
    // apply plugins in a safe manner: some subprojects may not be Kotlin-based,
    // but applying these plugins generally adds lint/detekt tasks if relevant.
    try {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
    } catch (_: Exception) {
        // ignore if plugin can't be applied for a given subproject
    }
    try {
        apply(plugin = "io.gitlab.arturbosch.detekt")
    } catch (_: Exception) {
        // ignore
    }
}
