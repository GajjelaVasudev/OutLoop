package com.outloop.prototype.continuous

/**
 * Constants and validation rules for Continuous Reels Break.
 */
object ContinuousBreakConstants {
    const val DEFAULT_INTERVAL_MINUTES = 5
    const val FIXED_BREAK_DURATION_SECONDS = 30
    const val MIN_INTERVAL_MINUTES = 1
    const val MAX_INTERVAL_MINUTES = 120

    /** Supported quick preset intervals in minutes. */
    val PRESET_INTERVALS = listOf(2, 3, 4, 5, 10, 15)
}

/**
 * Result of validating a custom continuous-break interval input.
 */
sealed class IntervalValidationResult {
    data class Valid(val minutes: Int) : IntervalValidationResult()
    data class Invalid(val errorMessage: String) : IntervalValidationResult()
}

/**
 * Validates a user-entered interval string.
 *
 * Rules:
 * - Accepts whole numbers between 1 and 120 minutes.
 * - Rejects empty or whitespace-only input.
 * - Rejects non-digit characters (including fractions/decimals like 2.5 and negative signs like -5).
 * - Rejects 0 or negative numbers.
 * - Rejects values exceeding 120 minutes.
 */
fun validateCustomInterval(input: String): IntervalValidationResult {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return IntervalValidationResult.Invalid("Please enter an interval in minutes.")
    }
    // Reject fractions, decimals, signs, or non-numeric characters
    if (!trimmed.all { it.isDigit() }) {
        return IntervalValidationResult.Invalid("Interval must be a whole number of minutes.")
    }
    val value = trimmed.toIntOrNull()
        ?: return IntervalValidationResult.Invalid("Interval is too large. Maximum is ${ContinuousBreakConstants.MAX_INTERVAL_MINUTES} minutes.")

    if (value < ContinuousBreakConstants.MIN_INTERVAL_MINUTES) {
        return IntervalValidationResult.Invalid("Interval must be at least ${ContinuousBreakConstants.MIN_INTERVAL_MINUTES} minute.")
    }
    if (value > ContinuousBreakConstants.MAX_INTERVAL_MINUTES) {
        return IntervalValidationResult.Invalid("Interval cannot exceed ${ContinuousBreakConstants.MAX_INTERVAL_MINUTES} minutes (2 hours).")
    }
    return IntervalValidationResult.Valid(value)
}
