package tilo.compose.render

/** Marks low-level rendering extension points that are not yet stable. */
@RequiresOptIn(
    message = "The low-level Tilo rendering API is experimental and may change without notice.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
annotation class ExperimentalTiloRenderingApi
