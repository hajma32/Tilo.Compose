package tilo.compose.dsl

/** Prevents accidental calls to outer Tilo DSL receivers from nested blocks. */
@DslMarker
@ExperimentalTiloApi
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class TiloDsl
