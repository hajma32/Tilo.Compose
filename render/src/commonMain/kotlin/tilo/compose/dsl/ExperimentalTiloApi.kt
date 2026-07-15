package tilo.compose.dsl

/**
 * Marks the Compose-first Tilo DSL as experimental.
 *
 * APIs carrying this annotation may change incompatibly between pre-release
 * versions. Consumers can acknowledge that risk with
 * `@OptIn(ExperimentalTiloApi::class)`.
 */
@RequiresOptIn(
    message = "The Tilo Compose DSL is experimental and may change without notice.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
annotation class ExperimentalTiloApi
