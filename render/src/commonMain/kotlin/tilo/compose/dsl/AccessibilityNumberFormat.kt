package tilo.compose.dsl

internal expect fun currentAccessibilityLocaleIdentifier(): String

internal expect fun formatAccessibilityNumber(
    value: Double,
    fractionDigits: Int,
    localeIdentifier: String,
): String
