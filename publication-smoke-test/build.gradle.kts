plugins {
    kotlin("multiplatform") version "2.3.0"
    id("com.android.library") version "8.11.2"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

val tiloVersion = providers.gradleProperty("tilo.version").orElse("0.1.0-alpha01")

kotlin {
    androidTarget()
    jvm()

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
    }
}

android {
    namespace = "eu.tilo.publication.smoke"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }
}
