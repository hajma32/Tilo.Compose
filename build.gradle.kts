import com.vanniktech.maven.publish.MavenPublishBaseExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.publish.PublishingExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import java.util.Properties

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.mavenPublish) apply false
    // code quality plugins (available for subprojects)
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

data class PublishedModule(
    val artifactId: String,
    val displayName: String,
    val description: String,
)

val publishedModules =
    mapOf(
        "spatial-index" to
            PublishedModule(
                artifactId = "tilo-compose-spatial-index",
                displayName = "Tilo Compose Spatial Index",
                description = "Multiplatform spatial indexing primitives for Tilo Compose.",
            ),
        "geocore" to
            PublishedModule(
                artifactId = "tilo-compose-geocore",
                displayName = "Tilo Compose GeoCore",
                description = "Geometry, projections, features, and map-domain primitives for Tilo Compose.",
            ),
        "core" to
            PublishedModule(
                artifactId = "tilo-compose-core",
                displayName = "Tilo Compose Core",
                description = "Network-backed raster sources and coordinate transformations for Tilo Compose.",
            ),
        "render" to
            PublishedModule(
                artifactId = "tilo-compose-render",
                displayName = "Tilo Compose Render",
                description = "Compose Multiplatform map rendering and the high-level Tilo map DSL.",
            ),
        "ui" to
            PublishedModule(
                artifactId = "tilo-compose",
                displayName = "Tilo Compose",
                description = "Compose Multiplatform maps, rendering, data sources, controls, and overlays.",
            ),
        "draw" to
            PublishedModule(
                artifactId = "tilo-compose-draw",
                displayName = "Tilo Compose Draw",
                description = "Interactive feature drawing state and layers for Tilo Compose.",
            ),
    )
val tiloGroup = providers.gradleProperty("tilo.group").get()
val tiloVersion = providers.gradleProperty("tilo.version").get()
val testRepository = layout.buildDirectory.dir("test-maven-repository")
val androidSdkDirectory =
    providers.environmentVariable("ANDROID_HOME").orElse(
        providers.fileContents(layout.projectDirectory.file("local.properties")).asText.map { contents ->
            Properties().apply { load(contents.reader()) }.getProperty("sdk.dir")
        },
    )

// Apply code quality plugins to subprojects where applicable
subprojects {
    if (name in publishedModules) {
        val publishedModule = publishedModules.getValue(name)
        group = tiloGroup
        version = tiloVersion

        pluginManager.withPlugin("com.vanniktech.maven.publish") {
            extensions.configure<MavenPublishBaseExtension> {
                coordinates(tiloGroup, publishedModule.artifactId, tiloVersion)
                publishToMavenCentral()
                if (providers.gradleProperty("signAllPublications").orNull.toBoolean()) {
                    signAllPublications()
                }
                pom {
                    name.set(publishedModule.displayName)
                    description.set(publishedModule.description)
                    inceptionYear.set("2026")
                    url.set("https://github.com/hajma32/Tilo.Compose")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/license/mit")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("hajma32")
                            name.set("Petr Heinz")
                            url.set("https://github.com/hajma32")
                        }
                    }
                    scm {
                        url.set("https://github.com/hajma32/Tilo.Compose")
                        connection.set("scm:git:https://github.com/hajma32/Tilo.Compose.git")
                        developerConnection.set("scm:git:ssh://git@github.com/hajma32/Tilo.Compose.git")
                    }
                }
            }
        }

        pluginManager.withPlugin("maven-publish") {
            extensions.configure<PublishingExtension> {
                repositories.maven {
                    name = "TiloTest"
                    url = testRepository.get().asFile.toURI()
                }
            }
        }
    }

    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    extensions.configure<KtlintExtension> {
        version.set("1.5.0")
        relative.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        filter {
            exclude("**/build/**")
            exclude("**/generated/**")
            exclude("**/generated-src/**")
            exclude { element -> element.file.invariantSeparatorsPath.contains("/build/generated/") }
        }
    }
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        ignoreFailures = false
        basePath = rootProject.projectDir.absolutePath
        config.setFrom(rootProject.file("detekt.yml"))
        source.setFrom(
            fileTree("src") {
                include("**/*.kt")
                exclude("**/generated/**")
                exclude("**/generated-src/**")
            },
        )
    }
    tasks.withType<Detekt>().configureEach {
        reports {
            html.required.set(true)
            sarif.required.set(true)
            txt.required.set(true)
            xml.required.set(false)
            md.required.set(false)
        }
    }
}

tasks.register("qualityFormat") {
    group = "formatting"
    description = "Formats Kotlin sources in every project with the repository ktlint rules."
    dependsOn(subprojects.map { project -> "${project.path}:ktlintFormat" })
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs deterministic Kotlin formatting and static-analysis gates for every project."
    dependsOn(subprojects.map { project -> "${project.path}:ktlintCheck" })
    dependsOn(subprojects.map { project -> "${project.path}:detekt" })
}

tasks.register<Delete>("cleanTiloTestRepository") {
    group = "publishing"
    description = "Deletes the isolated Maven repository used by publication verification."
    delete(testRepository)
}

tasks.register("publishTiloToTestRepository") {
    group = "publishing"
    description = "Publishes every supported Tilo artifact to the isolated test repository."
    dependsOn(publishedModules.keys.map { ":$it:publishAllPublicationsToTiloTestRepository" })
}

tasks.register<Exec>("verifyMavenPublication") {
    group = "verification"
    description = "Publishes Tilo and compiles a separate coordinates-only consumer."
    dependsOn("publishTiloToTestRepository")
    workingDir = rootDir
    commandLine(
        rootDir.resolve("gradlew").absolutePath,
        "-p",
        "publication-smoke-test",
        "--no-daemon",
        "--no-configuration-cache",
        "-Ptilo.version=$tiloVersion",
        "clean",
        "compileDebugKotlinAndroid",
        "compileKotlinJvm",
    )
    environment("ANDROID_HOME", androidSdkDirectory.get())
}
