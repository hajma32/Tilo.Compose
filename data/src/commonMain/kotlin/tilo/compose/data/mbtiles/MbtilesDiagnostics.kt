package tilo.compose.data.mbtiles

object MbtilesDiagnostics {
    var enabled: Boolean = false

    fun log(message: String) {
        if (!enabled) return
        println("[MBTiles] $message")
    }
}

