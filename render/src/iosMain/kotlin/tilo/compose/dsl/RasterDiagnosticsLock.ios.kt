package tilo.compose.dsl

import platform.Foundation.NSRecursiveLock

internal actual fun rasterDiagnosticsLock(): RasterDiagnosticsLock = IosRasterDiagnosticsLock()

private class IosRasterDiagnosticsLock : RasterDiagnosticsLock {
    private val lock = NSRecursiveLock()

    override fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}
