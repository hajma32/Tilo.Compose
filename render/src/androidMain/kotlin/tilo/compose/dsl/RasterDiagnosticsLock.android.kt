package tilo.compose.dsl

internal actual fun rasterDiagnosticsLock(): RasterDiagnosticsLock = AndroidRasterDiagnosticsLock()

private class AndroidRasterDiagnosticsLock : RasterDiagnosticsLock {
    private val monitor = Any()

    override fun <T> withLock(block: () -> T): T = synchronized(monitor, block)
}
