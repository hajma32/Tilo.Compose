package tilo.compose.dsl

import java.text.NumberFormat
import java.util.Locale

internal actual fun currentAccessibilityLocaleIdentifier(): String = Locale.getDefault().toLanguageTag()

internal actual fun formatAccessibilityNumber(
    value: Double,
    fractionDigits: Int,
    localeIdentifier: String,
): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag(localeIdentifier)).run {
        isGroupingUsed = false
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
        format(value)
    }
