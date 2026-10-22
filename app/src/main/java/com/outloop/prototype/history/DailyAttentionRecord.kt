package com.outloop.prototype.history

/**
 * Aggregated on-device record of a single day's attention and Reels usage metrics.
 *
 * PRIVACY GUARANTEES:
 * - Stores ZERO Instagram messages, captions, private text, screenshots, or node content.
 * - Stores strictly aggregated mathematical durations and discrete event counts.
 * - Kept strictly on-device in private SharedPreferences.
 */
data class DailyAttentionRecord(
    val date: String, // ISO "YYYY-MM-DD"
    val accumulatedReelsMs: Long = 0L,
    val normalLimitMinutes: Long = 0L,
    val extraAllowanceMinutes: Long = 0L,
    val effectiveLimitMinutes: Long = 0L,
    val isAllowanceEnabled: Boolean = false,
    val isRestrictionRelaxed: Boolean = false,
    val allowanceExhausted: Boolean = false,
    val restrictionDisplayedCount: Int = 0,
    val restrictionDismissedCount: Int = 0,
    val scrollRescueResetsCompleted: Int = 0,
    val hasSufficientTrackingCoverage: Boolean = true,
    val clockDiscrepancyObserved: Boolean = false
) {
    /**
     * Returns true if user stayed within their effective allowance on this day.
     * Note: Relaxed restrictions or authorized extra time count as legitimate compliance.
     */
    val wasAllowanceRespected: Boolean
        get() = isRestrictionRelaxed || (effectiveLimitMinutes > 0L && accumulatedReelsMs <= (effectiveLimitMinutes * 60 * 1000L))
}
