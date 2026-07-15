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

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    )

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":geocore"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
            }
        }
        val androidMain by getting {
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
