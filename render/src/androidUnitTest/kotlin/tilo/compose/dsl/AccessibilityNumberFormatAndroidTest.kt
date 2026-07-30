package tilo.compose.dsl

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class AccessibilityNumberFormatAndroidTest {
    @Test
    fun usesCurrentLocaleAndRequestedFractionDigits() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)

            val localeIdentifier = currentAccessibilityLocaleIdentifier()
            assertEquals("de-DE", localeIdentifier)
            assertEquals(
                "3,5",
                formatAccessibilityNumber(3.5, fractionDigits = 1, localeIdentifier = localeIdentifier),
            )
            assertEquals(
                "16",
                formatAccessibilityNumber(16.0, fractionDigits = 0, localeIdentifier = localeIdentifier),
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
