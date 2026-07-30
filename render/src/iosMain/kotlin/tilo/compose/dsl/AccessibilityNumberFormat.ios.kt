package tilo.compose.dsl

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

internal actual fun currentAccessibilityLocaleIdentifier(): String = NSLocale.currentLocale().localeIdentifier

internal actual fun formatAccessibilityNumber(
    value: Double,
    fractionDigits: Int,
    localeIdentifier: String,
): String =
    NSNumberFormatter().run {
        locale = NSLocale(localeIdentifier = localeIdentifier)
        numberStyle = NSNumberFormatterDecimalStyle
        usesGroupingSeparator = false
        minimumFractionDigits = fractionDigits.toULong()
        maximumFractionDigits = fractionDigits.toULong()
        stringFromNumber(NSNumber(double = value)) ?: value.toString()
    }
