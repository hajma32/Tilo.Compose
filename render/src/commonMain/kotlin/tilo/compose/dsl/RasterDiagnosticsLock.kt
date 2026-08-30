package tilo.compose.dsl

/** Reentrant platform lock used to make lifecycle transitions and callback claims indivisible. */
internal interface RasterDiagnosticsLock {
    fun <T> withLock(block: () -> T): T
}

internal expect fun rasterDiagnosticsLock(): RasterDiagnosticsLock
