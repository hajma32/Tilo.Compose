pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            name = "TiloTest"
            url = uri("../build/test-maven-repository")
            content { includeGroup("eu.tilomaps") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "tilo-publication-smoke-test"
