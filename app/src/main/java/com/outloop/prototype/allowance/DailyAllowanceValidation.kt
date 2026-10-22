package com.outloop.prototype.allowance

/**
 * Validation rules and limits for custom Daily Reels Allowance.
 */
object DailyAllowanceConstants {
    const val MIN_DAILY_LIMIT_MINUTES = 1L
    const val MAX_DAILY_LIMIT_MINUTES = 1440L // 24 hours (1440 minutes)
}

sealed class DailyLimitValidationResult {
    data class Valid(val minutes: Long) : DailyLimitValidationResult()
    data class Invalid(val errorMessage: String) : DailyLimitValidationResult()
}

/**
 * Validates a user-entered custom daily allowance limit string.
 *
 * Rules:
 * - Must be a whole number between 1 and 1440 minutes (1 minute to 24 hours).
 * - Rejects empty or whitespace-only input.
 * - Rejects fractions, decimals, letters, negative signs, or symbols.
 * - Rejects 0 or negative numbers.
 * - Rejects values exceeding 1440 minutes.
 */
fun validateCustomDailyLimit(input: String): DailyLimitValidationResult {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return DailyLimitValidationResult.Invalid("Please enter a daily limit in minutes.")
    }
    if (!trimmed.all { it.isDigit() }) {
        return DailyLimitValidationResult.Invalid("Limit must be a whole number of minutes.")
    }
    val value = trimmed.toLongOrNull()
        ?: return DailyLimitValidationResult.Invalid("Limit is too large. Maximum is ${DailyAllowanceConstants.MAX_DAILY_LIMIT_MINUTES} minutes (24 hours).")

    if (value < DailyAllowanceConstants.MIN_DAILY_LIMIT_MINUTES) {
        return DailyLimitValidationResult.Invalid("Limit must be at least ${DailyAllowanceConstants.MIN_DAILY_LIMIT_MINUTES} minute.")
    }
    if (value > DailyAllowanceConstants.MAX_DAILY_LIMIT_MINUTES) {
        return DailyLimitValidationResult.Invalid("Limit cannot exceed ${DailyAllowanceConstants.MAX_DAILY_LIMIT_MINUTES} minutes (24 hours).")
    }
    return DailyLimitValidationResult.Valid(value)
}
