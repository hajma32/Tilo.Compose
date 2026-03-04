package tilo.compose.core.feature

/**
 * Callout holds a generic rendering lambda that receives the Feature.
 * Platform modules (Compose) can wrap a @Composable lambda into this type via helpers.
 */
data class Callout(val render: (feature: Feature) -> Any)
