package com.outloop.prototype.continuous

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [validateCustomInterval] and [ContinuousBreakConstants].
 */
class ContinuousBreakValidationTest {

    @Test
    fun testPresetIntervalsList() {
        val expectedPresets = listOf(2, 3, 4, 5, 10, 15)
        assertEquals("Preset intervals must match required 2, 3, 4, 5, 10, 15", expectedPresets, ContinuousBreakConstants.PRESET_INTERVALS)
        assertEquals(5, ContinuousBreakConstants.DEFAULT_INTERVAL_MINUTES)
        assertEquals(30, ContinuousBreakConstants.FIXED_BREAK_DURATION_SECONDS)
    }

    @Test
    fun testValidIntervalValues() {
        val validValues = listOf("1", "2", "5", "10", "30", "60", "120")
        for (input in validValues) {
            val result = validateCustomInterval(input)
            assertTrue("Input '$input' should be valid", result is IntervalValidationResult.Valid)
            assertEquals(input.toInt(), (result as IntervalValidationResult.Valid).minutes)
        }
    }

    @Test
    fun testWhitespacePaddedValidValues() {
        val result = validateCustomInterval("  15  ")
        assertTrue(result is IntervalValidationResult.Valid)
        assertEquals(15, (result as IntervalValidationResult.Valid).minutes)
    }

    @Test
    fun testEmptyAndBlankInputsAreRejected() {
        val emptyResult = validateCustomInterval("")
        assertTrue(emptyResult is IntervalValidationResult.Invalid)

        val blankResult = validateCustomInterval("   ")
        assertTrue(blankResult is IntervalValidationResult.Invalid)
    }

    @Test
    fun testFractionalValuesAreRejected() {
        val fractionResult1 = validateCustomInterval("2.5")
        assertTrue(fractionResult1 is IntervalValidationResult.Invalid)

        val fractionResult2 = validateCustomInterval("5.0")
        assertTrue(fractionResult2 is IntervalValidationResult.Invalid)

        val fractionResult3 = validateCustomInterval("1/2")
        assertTrue(fractionResult3 is IntervalValidationResult.Invalid)
    }

    @Test
    fun testZeroAndNegativeValuesAreRejected() {
        val zeroResult = validateCustomInterval("0")
        assertTrue(zeroResult is IntervalValidationResult.Invalid)

        val negResult1 = validateCustomInterval("-1")
        assertTrue(negResult1 is IntervalValidationResult.Invalid)

        val negResult2 = validateCustomInterval("-15")
        assertTrue(negResult2 is IntervalValidationResult.Invalid)
    }

    @Test
    fun testNonNumericInputsAreRejected() {
        val words = listOf("abc", "five", "10m", "15min", "2,5", "!")
        for (input in words) {
            val result = validateCustomInterval(input)
            assertTrue("Input '$input' should be rejected", result is IntervalValidationResult.Invalid)
        }
    }

    @Test
    fun testOutOfRangeAbove120IsRejected() {
        val outOfRange1 = validateCustomInterval("121")
        assertTrue(outOfRange1 is IntervalValidationResult.Invalid)

        val outOfRange2 = validateCustomInterval("500")
        assertTrue(outOfRange2 is IntervalValidationResult.Invalid)

        val huge = validateCustomInterval("999999999999")
        assertTrue(huge is IntervalValidationResult.Invalid)
    }
}
