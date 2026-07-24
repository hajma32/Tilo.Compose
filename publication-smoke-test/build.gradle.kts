plugins {
    kotlin("multiplatform") version "2.3.0"
    id("com.android.library") version "8.11.2"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

val tiloVersion = providers.gradleProperty("tilo.version").orElse("0.1.2-alpha06")

kotlin {
    androidTarget()
    jvm()
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "TiloPublicationSmoke"
            isStatic = true
        }
    }

    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation("eu.tilomaps:tilo-compose:${tiloVersion.get()}")
                implementation("eu.tilomaps:tilo-compose-draw:${tiloVersion.get()}")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("eu.tilomaps:tilo-compose-draw:${tiloVersion.get()}")
            }
        }
        val iosSimulatorArm64Main by getting {
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:1.10.0")
                implementation("eu.tilomaps:tilo-compose-core:${tiloVersion.get()}")
            }
        }
    }
}

android {
    namespace = "eu.tilo.publication.smoke"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }
}
