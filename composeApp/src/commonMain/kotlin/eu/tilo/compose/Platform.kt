package eu.tilo.compose

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
