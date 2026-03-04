plugins {
    kotlin("multiplatform") version "2.3.0"
}

kotlin {
    jvm()
    // Keep native removed to avoid unnecessary complexity; add ios/android targets later as needed

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
