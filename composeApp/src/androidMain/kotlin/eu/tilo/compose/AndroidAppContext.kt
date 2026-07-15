package eu.tilo.compose

import android.content.Context

/** Holds application context for Android-only loaders initialized from MainActivity. */
object AndroidAppContext {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun require(): Context =
        checkNotNull(appContext) {
            "AndroidAppContext is not initialized. Call AndroidAppContext.init(...) from MainActivity."
        }
}
