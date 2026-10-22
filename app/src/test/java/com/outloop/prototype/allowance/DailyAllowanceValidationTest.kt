package com.outloop.prototype.allowance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic unit tests for [validateCustomDailyLimit] and [DailyAllowanceConstants].
 */
class DailyAllowanceValidationTest {

    @Test
    fun testValidPresetsAndCustomLimits() {
        val validInputs = listOf(
            "1" to 1L,
            "2" to 2L,
            "3" to 3L,
            "15" to 15L,
            "30" to 30L,
            "45" to 45L,
            "60" to 60L,
            "120" to 120L,
            "720" to 720L,
            "1440" to 1440L,
            "  45  " to 45L // Whitespace trimmed
        )

        for ((input, expectedMinutes) in validInputs) {
            val result = validateCustomDailyLimit(input)
            assertTrue("Expected Valid for input '$input'", result is DailyLimitValidationResult.Valid)
            assertEquals(expectedMinutes, (result as DailyLimitValidationResult.Valid).minutes)
        }
    }

    @Test
    fun testRejectEmptyAndWhitespace() {
        val emptyInputs = listOf("", "   ", "\t", "\n")
        for (input in emptyInputs) {
            val result = validateCustomDailyLimit(input)
            assertTrue("Expected Invalid for input '$input'", result is DailyLimitValidationResult.Invalid)
            assertEquals("Please enter a daily limit in minutes.", (result as DailyLimitValidationResult.Invalid).errorMessage)
        }
    }

    @Test
    fun testRejectNonDigitsFractionsAndNegatives() {
        val invalidInputs = listOf(
            "1.5",
            "2,5",
            "-1",
            "-15",
            "abc",
            "10m",
            "1h",
            "1 0",
            "+15"
        )
        for (input in invalidInputs) {
            val result = validateCustomDailyLimit(input)
            assertTrue("Expected Invalid for input '$input'", result is DailyLimitValidationResult.Invalid)
            assertEquals("Limit must be a whole number of minutes.", (result as DailyLimitValidationResult.Invalid).errorMessage)
        }
    }

    @Test
    fun testRejectZero() {
        val result = validateCustomDailyLimit("0")
        assertTrue("Expected Invalid for 0", result is DailyLimitValidationResult.Invalid)
        assertEquals("Limit must be at least 1 minute.", (result as DailyLimitValidationResult.Invalid).errorMessage)
    }

    @Test
    fun testRejectValuesExceedingMaxLimit() {
        val outOfRangeInputs = listOf(
            "1441",
            "2000",
            "99999",
            "99999999999999999999999" // Long overflow
        )
        for (input in outOfRangeInputs) {
            val result = validateCustomDailyLimit(input)
            assertTrue("Expected Invalid for '$input'", result is DailyLimitValidationResult.Invalid)
        }
    }
}
