package tilo.compose.draw

/** Marks the optional interactive drawing API, which is not yet stable. */
@RequiresOptIn(
    message = "The Tilo drawing API is experimental and may change without notice.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
annotation class ExperimentalTiloDrawApi
