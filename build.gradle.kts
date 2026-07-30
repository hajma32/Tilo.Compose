import com.vanniktech.maven.publish.MavenPublishBaseExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import java.net.URI
import java.util.Properties

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.mavenPublish) apply false
    // code quality plugins (available for subprojects)
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

dokka {
    dokkaPublications.html {
        moduleName.set("Tilo Compose")
        failOnWarning.set(true)
    }
}

dependencies {
    dokka(project(":spatial-index"))
    dokka(project(":geocore"))
    dokka(project(":core"))
    dokka(project(":render"))
    dokka(project(":ui"))
    dokka(project(":draw"))
}

data class PublishedModule(
    val artifactId: String,
    val displayName: String,
    val description: String,
)

abstract class VerifyPublicApiDocumentation : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectories: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val declaration =
            Regex(
                """^(?!(?:private|internal)\b)(?:(?:public|expect|actual|data|sealed|enum|value|annotation|fun|infix|operator|suspend|tailrec)\s+)*(?:class|interface|object|fun|typealias)\b""",
            )
        val undocumented = mutableListOf<String>()

        sourceDirectories.asFileTree
            .matching { include("**/*.kt") }
            .sortedBy { it.invariantSeparatorsPath }
            .forEach { sourceFile ->
                val lines = sourceFile.readLines()
                lines.forEachIndexed { index, line ->
                    if (!declaration.containsMatchIn(line)) return@forEachIndexed

                    var previous = index - 1
                    while (previous >= 0) {
                        while (previous >= 0 && lines[previous].isBlank()) previous--
                        if (previous < 0) break
                        if (lines[previous].trimStart().startsWith("@")) {
                            previous--
                            continue
                        }
                        if (lines[previous].trim() == ")") {
                            var depth = 1
                            previous--
                            while (previous >= 0 && depth > 0) {
                                depth += lines[previous].count { it == ')' }
                                depth -= lines[previous].count { it == '(' }
                                previous--
                            }
                            continue
                        }
                        break
                    }

                    var commentStart = previous
                    if (previous >= 0 && lines[previous].trimEnd().endsWith("*/")) {
                        while (commentStart >= 0 && !lines[commentStart].trimStart().startsWith("/*")) {
                            commentStart--
                        }
                    }
                    if (commentStart < 0 || !lines[commentStart].trimStart().startsWith("/**")) {
                        undocumented += "${sourceFile.invariantSeparatorsPath}:${index + 1}: ${line.trim()}"
                    }
                }
            }

        check(undocumented.isEmpty()) {
            "Public common API declarations must have KDoc:\n${undocumented.joinToString("\n")}"
        }
    }
}

abstract class VerifyDokkaLinks : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val documentationDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val root = documentationDirectory.get().asFile
        val htmlFiles = root.walkTopDown().filter { it.isFile && it.extension == "html" }.toList()
        val expectedModules = setOf("spatial-index", "geocore", "core", "render", "ui", "draw")
        val missingModules = expectedModules.filterNot { root.resolve(it).isDirectory }
        val unresolvedTiloLinks = mutableListOf<String>()
        val missingTargets = mutableListOf<String>()
        val hrefPattern = Regex("""href=[\"']([^\"']+)[\"']""")

        htmlFiles.forEach { htmlFile ->
            val html = htmlFile.readText()
            if ("data-unresolved-link=\"tilo." in html) {
                unresolvedTiloLinks += htmlFile.relativeTo(root).invariantSeparatorsPath
            }
            hrefPattern.findAll(html).forEach hrefLoop@{ match ->
                val href = match.groupValues[1].substringBefore('#').substringBefore('?')
                if (
                    href.isEmpty() || href.startsWith("/") || href.contains("://") ||
                    href.startsWith("mailto:") || href.startsWith("javascript:")
                ) {
                    return@hrefLoop
                }
                val target = htmlFile.parentFile.toPath().resolve(href).normalize().toFile()
                if (!target.exists()) {
                    missingTargets += "${htmlFile.relativeTo(root).invariantSeparatorsPath} -> $href"
                }
            }
        }

        check(missingModules.isEmpty() && unresolvedTiloLinks.isEmpty() && missingTargets.isEmpty()) {
            buildString {
                if (missingModules.isNotEmpty()) appendLine("Missing Dokka modules: ${missingModules.joinToString()}")
                if (unresolvedTiloLinks.isNotEmpty()) {
                    appendLine("Unresolved Tilo API links:")
                    appendLine(unresolvedTiloLinks.joinToString("\n"))
                }
                if (missingTargets.isNotEmpty()) {
                    appendLine("Broken relative links:")
                    appendLine(missingTargets.joinToString("\n"))
                }
            }.trimEnd()
        }
    }
}

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
val tiloVersion = libs.versions.tilo.get()
val testRepository = layout.buildDirectory.dir("test-maven-repository")
val canCompileAppleTargets = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
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
            tasks.matching { it.name == "publishAllPublicationsToTiloTestRepository" }.configureEach {
                mustRunAfter(":cleanTiloTestRepository")
            }
        }

        tasks.withType<Jar>().configureEach {
            from(rootProject.layout.projectDirectory.file("LICENSE")) {
                into("META-INF")
            }
        }

        tasks.withType<Zip>().matching { it.name.endsWith("Aar") || it.name.endsWith("Klib") }.configureEach {
            from(rootProject.layout.projectDirectory.file("LICENSE")) {
                into(if (name.endsWith("Klib")) "default/resources/META-INF" else "META-INF")
            }
        }
    }

    pluginManager.withPlugin("org.jetbrains.dokka") {
        extensions.configure<DokkaExtension> {
            dokkaSourceSets.configureEach {
                documentedVisibilities.set(setOf(VisibilityModifier.Public))
                if (
                    !canCompileAppleTargets &&
                    listOf("ios", "apple", "native").any { platform -> name.contains(platform, ignoreCase = true) }
                ) {
                    suppress.set(true)
                }
                sourceLink {
                    localDirectory.set(project.layout.projectDirectory.dir("src"))
                    val remoteSourceDirectory =
                        when (project.name) {
                            "spatial-index" -> "https://github.com/hajma32/Tilo.SpatialIndex/tree/main/src"
                            else -> "https://github.com/hajma32/Tilo.Compose/tree/main/${project.name}/src"
                        }
                    remoteUrl.set(URI(remoteSourceDirectory))
                    remoteLineSuffix.set("#L")
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

val publishedCommonMainSources =
    publishedModules.keys.map { moduleName ->
        project(moduleName).layout.projectDirectory.dir("src/commonMain/kotlin")
    }

tasks.register<VerifyPublicApiDocumentation>("verifyPublicApiDocumentation") {
    group = "verification"
    description = "Checks that every public top-level common API declaration has KDoc."
    sourceDirectories.from(publishedCommonMainSources)
}

tasks.register<VerifyDokkaLinks>("verifyDokkaLinks") {
    group = "verification"
    description = "Checks module presence, Tilo symbol resolution, and relative links in Dokka HTML."
    dependsOn("dokkaGenerate")
    documentationDirectory.set(layout.buildDirectory.dir("dokka/html"))
}

tasks.register("documentationCheck") {
    group = "verification"
    description = "Verifies public API KDoc and generates linked multi-module documentation."
    dependsOn("verifyPublicApiDocumentation", "verifyDokkaLinks")
}

tasks.register<Delete>("cleanTiloTestRepository") {
    group = "publishing"
    description = "Deletes the isolated Maven repository used by publication verification."
    delete(testRepository)
}

tasks.register("publishTiloToTestRepository") {
    group = "publishing"
    description = "Publishes every supported Tilo artifact to the isolated test repository."
    dependsOn("cleanTiloTestRepository")
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
        "linkDebugFrameworkIosSimulatorArm64",
    )
    environment("ANDROID_HOME", androidSdkDirectory.get())
}
