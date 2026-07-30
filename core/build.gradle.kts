plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.dokka)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }

    val projXCFramework = layout.projectDirectory.dir("src/nativeInterop/proj/PROJ.xcframework")
    val projDefinition = layout.projectDirectory.file("src/nativeInterop/cinterop/proj.def")

    iosArm64 {
        compilations.getByName("main").cinterops.create("proj") {
            definitionFile.set(projDefinition)
            includeDirs(projXCFramework.dir("ios-arm64/Headers"))
            extraOpts(
                "-libraryPath",
                projXCFramework.dir("ios-arm64").asFile.absolutePath,
                "-staticLibrary",
                "libproj.a",
            )
        }
        binaries.configureEach {
            linkerOpts("-lsqlite3", "-lc++")
        }
    }

    iosSimulatorArm64 {
        compilations.getByName("main").cinterops.create("proj") {
            definitionFile.set(projDefinition)
            includeDirs(projXCFramework.dir("ios-arm64-simulator/Headers"))
            extraOpts(
                "-libraryPath",
                projXCFramework.dir("ios-arm64-simulator").asFile.absolutePath,
                "-staticLibrary",
                "libproj.a",
            )
        }
        binaries.configureEach {
            linkerOpts("-lsqlite3", "-lc++")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":geocore"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
            }
        }
        val androidMain by getting {
            resources.srcDir("src/thirdPartyLicenses/common")
            resources.srcDir("src/thirdPartyLicenses/android")
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.proj4j)
                implementation(libs.proj4j.epsg)
            }
        }
        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }
        val iosTest by creating {
            dependsOn(commonTest)
        }
        val iosArm64Test by getting {
            dependsOn(iosTest)
        }
        val iosSimulatorArm64Test by getting {
            dependsOn(iosTest)
        }
    }
}

android {
    namespace = "tilo.compose.core"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
}

val projIosArm64Manifest =
    layout.buildDirectory.file("classes/kotlin/iosArm64/main/cinterop/core-cinterop-proj/default/manifest")

val copyProjLegalNoticesIosArm64 =
    tasks.register<Copy>("copyProjLegalNoticesIosArm64") {
        dependsOn("cinteropProjIosArm64")
        inputs
            .file(projIosArm64Manifest)
            .withPropertyName("projCinteropManifest")
            .optional()
        onlyIf("the iOS device PROJ cinterop KLIB was produced") { task ->
            task.inputs.files.files
                .any { it.name == "manifest" && it.isFile }
        }
        from(
            "src/nativeInterop/proj/LICENSE-PROJ.txt",
            "src/nativeInterop/proj/NOTICE-EPSG.txt",
            "src/thirdPartyLicenses/common/META-INF/third-party/LICENSE-EPSG.txt",
        )
        into(
            layout.buildDirectory.dir(
                "classes/kotlin/iosArm64/main/cinterop/core-cinterop-proj/" +
                    "default/resources/META-INF/third-party",
            ),
        )
    }

val projIosSimulatorArm64Manifest =
    layout.buildDirectory.file("classes/kotlin/iosSimulatorArm64/main/cinterop/core-cinterop-proj/default/manifest")

val copyProjLegalNoticesIosSimulatorArm64 =
    tasks.register<Copy>("copyProjLegalNoticesIosSimulatorArm64") {
        dependsOn("cinteropProjIosSimulatorArm64")
        inputs
            .file(projIosSimulatorArm64Manifest)
            .withPropertyName("projCinteropManifest")
            .optional()
        onlyIf("the iOS simulator PROJ cinterop KLIB was produced") { task ->
            task.inputs.files.files
                .any { it.name == "manifest" && it.isFile }
        }
        from(
            "src/nativeInterop/proj/LICENSE-PROJ.txt",
            "src/nativeInterop/proj/NOTICE-EPSG.txt",
            "src/thirdPartyLicenses/common/META-INF/third-party/LICENSE-EPSG.txt",
        )
        into(
            layout.buildDirectory.dir(
                "classes/kotlin/iosSimulatorArm64/main/cinterop/core-cinterop-proj/" +
                    "default/resources/META-INF/third-party",
            ),
        )
    }

tasks.named("iosArm64Cinterop-projKlib") {
    dependsOn(copyProjLegalNoticesIosArm64)
}

tasks.named("iosSimulatorArm64Cinterop-projKlib") {
    dependsOn(copyProjLegalNoticesIosSimulatorArm64)
}

tasks.named("compileKotlinIosArm64") {
    dependsOn(copyProjLegalNoticesIosArm64)
}

tasks.named("compileKotlinIosSimulatorArm64") {
    dependsOn(copyProjLegalNoticesIosSimulatorArm64)
}

tasks.matching { it.name == "commonizeCInterop" }.configureEach {
    dependsOn(copyProjLegalNoticesIosArm64, copyProjLegalNoticesIosSimulatorArm64)
}
