package com.outloop.prototype.reminder

import com.outloop.classifier.CandidateScreenState
import com.outloop.classifier.ClassificationResult
import com.outloop.classifier.Confidence
import com.outloop.classifier.ObservationOutcome
import com.outloop.classifier.ReasonCode

/**
 * Pure Kotlin monotonic session tracker for accumulating confirmed time on Instagram's dedicated Reels tab.
 *
 * ARCHITECTURAL SAFETY & ACCURACY GUARANTEES:
 * 1. Monotonic elapsed time: Uses an injectable monotonic clock (defaulting to SystemClock.elapsedRealtime)
 *    to prevent time skips from NTP or system clock modifications.
 * 2. Bounded deltas: Caps observation intervals at [staleObservationTimeoutMs] (default 10s) to prevent
 *    crediting silent or sleep intervals.
 * 3. Screen-exit accounting: When transitioning away from Reels to another screen, credits the bounded trailing
 *    interval spent on Reels prior to exit.
 * 4. Stale-observation timeout: An old Reels classification cannot authorize indefinite time accumulation
 *    or trigger a reminder during silence. Querying state after timeout evaluates to [SessionState.PAUSED].
 * 5. Inactivity reset: If the user remains away from Reels or outside Instagram for more than
 *    [inactivityResetTimeoutMs] (default 15 minutes), querying the tracker resets accumulated time to 0.
 * 6. True opt-in defaults: Both reminders and advance warnings default to OFF (false).
 * 7. Deduplication: Duplicate accessibility events arriving with zero delta are handled idempotently.
 * 8. Threshold guarantee: Each reminder threshold fires at most once per session.
 */
class ReelsSessionTracker(
    private val clock: () -> Long,
    val advanceWarningMs: Long = DEFAULT_ADVANCE_WARNING_MS,
    val firstReminderMs: Long = DEFAULT_FIRST_REMINDER_MS,
    val repeatIntervalMs: Long = DEFAULT_REPEAT_INTERVAL_MS,
    val staleObservationTimeoutMs: Long = DEFAULT_STALE_TIMEOUT_MS,
    val inactivityResetTimeoutMs: Long = DEFAULT_INACTIVITY_RESET_TIMEOUT_MS,
    private val logger: (String) -> Unit = {}
) {

    companion object {
        const val DEFAULT_ADVANCE_WARNING_MS = 9 * 60 * 1000L   // 9 minutes
        const val DEFAULT_FIRST_REMINDER_MS = 10 * 60 * 1000L   // 10 minutes
        const val DEFAULT_REPEAT_INTERVAL_MS = 5 * 60 * 1000L   // 5 minutes
        const val DEFAULT_STALE_TIMEOUT_MS = 10 * 1000L          // 10 seconds
        const val DEFAULT_INACTIVITY_RESET_TIMEOUT_MS = 15 * 60 * 1000L // 15 minutes
    }

    enum class SessionState {
        IDLE,
        TRACKING,
        PAUSED
    }

    enum class ReminderType {
        ADVANCE_WARNING,
        FIRST_REMINDER,
        REPEAT_REMINDER
    }

    data class ReminderEvent(
        val type: ReminderType,
        val thresholdMs: Long,
        val accumulatedTimeMs: Long,
        val timestampMs: Long
    )

    private val lock = Any()

    private var _baseAccumulatedTimeMs: Long = 0L
    private var _sessionState: SessionState = SessionState.IDLE
    private var lastEligibleObservationTimeMs: Long? = null
    private var lastNonReelsObservationTimeMs: Long? = null
    private val firedThresholds = mutableSetOf<Long>()

    /**
     * Opt-in preference: Master toggle for reminder delivery.
     * MUST default to false (true opt-in).
     */
    var isReminderEnabled: Boolean = false

    /**
     * Opt-in preference: 1-minute advance warning (at 9 minutes).
     * MUST default to false (true opt-in).
     */
    var isAdvanceWarningEnabled: Boolean = false

    /**
     * Evaluates the current state of the session against monotonic time [now].
     * Synchronizes bounded lookahead deltas, enforces stale timeouts, and handles inactivity resets.
     *
     * This is a thread-safe method called internally by property getters and observation handlers.
     */
    fun syncState(now: Long = clock()) {
        synchronized(lock) {
            // 1. Inactivity Timeout: if user has been away from Reels longer than inactivityResetTimeoutMs
            val lastSeen = lastEligibleObservationTimeMs ?: lastNonReelsObservationTimeMs
            if (lastSeen != null && (now - lastSeen > inactivityResetTimeoutMs)) {
                _baseAccumulatedTimeMs = 0L
                _sessionState = SessionState.IDLE
                lastEligibleObservationTimeMs = null
                lastNonReelsObservationTimeMs = null
                firedThresholds.clear()
                return
            }

            // 2. Stale Observation Timeout: while in TRACKING, check if the active observation window expired
            if (_sessionState == SessionState.TRACKING) {
                val lastEligible = lastEligibleObservationTimeMs
                if (lastEligible != null) {
                    val elapsed = now - lastEligible
                    if (elapsed >= staleObservationTimeoutMs) {
                        // Settle bounded slice and transition to PAUSED
                        _baseAccumulatedTimeMs += staleObservationTimeoutMs
                        _sessionState = SessionState.PAUSED
                        lastNonReelsObservationTimeMs = lastEligible + staleObservationTimeoutMs
                        lastEligibleObservationTimeMs = null
                    }
                }
            }
        }
    }

    val sessionState: SessionState
        get() {
            syncState()
            return synchronized(lock) { _sessionState }
        }

    val accumulatedReelsTimeMs: Long
        get() {
            syncState()
            return synchronized(lock) {
                if (_sessionState == SessionState.TRACKING && lastEligibleObservationTimeMs != null) {
                    val activeElapsed = clock() - lastEligibleObservationTimeMs!!
                    _baseAccumulatedTimeMs + minOf(activeElapsed, staleObservationTimeoutMs)
                } else {
                    _baseAccumulatedTimeMs
                }
            }
        }

    /**
     * Explicitly resets accumulated session time, session state, and fired threshold history.
     */
    fun resetSession() {
        synchronized(lock) {
            _baseAccumulatedTimeMs = 0L
            _sessionState = SessionState.IDLE
            lastEligibleObservationTimeMs = null
            lastNonReelsObservationTimeMs = null
            firedThresholds.clear()
        }
    }

    /**
     * Processes an incoming sanitized [ClassificationResult].
     *
     * @return A list of [ReminderEvent]s triggered by this observation, if any.
     */
    fun onClassificationResult(result: ClassificationResult): List<ReminderEvent> {
        val now = clock()
        synchronized(lock) {
            // 1. Synchronize state to apply any pending inactivity reset or stale timeout
            syncState(now)

            val isEligible = isEligibleReelsObservation(result)

            if (!isEligible) {
                // If transitioning from TRACKING to PAUSED on non-Reels observation:
                // Credit the bounded trailing interval spent on Reels prior to exit
                if (_sessionState == SessionState.TRACKING && lastEligibleObservationTimeMs != null) {
                    val elapsed = now - lastEligibleObservationTimeMs!!
                    if (elapsed > 0) {
                        _baseAccumulatedTimeMs += minOf(elapsed, staleObservationTimeoutMs)
                    }
                }
                _sessionState = SessionState.PAUSED
                lastEligibleObservationTimeMs = null
                lastNonReelsObservationTimeMs = now
                // Non-Reels, paused, or exit observations must NEVER trigger a reminder
                return emptyList()
            }

            // 2. Eligible high-confidence Reels observation
            lastNonReelsObservationTimeMs = null

            if (_sessionState == SessionState.TRACKING && lastEligibleObservationTimeMs != null) {
                val elapsed = now - lastEligibleObservationTimeMs!!
                if (elapsed > 0) {
                    _baseAccumulatedTimeMs += minOf(elapsed, staleObservationTimeoutMs)
                }
                lastEligibleObservationTimeMs = now
            } else {
                // Resuming from PAUSED or IDLE:
                // Anchor new tracking interval from now; do NOT credit paused duration
                _sessionState = SessionState.TRACKING
                lastEligibleObservationTimeMs = now
            }

            // If reminders are disabled, evaluate no thresholds
            if (!isReminderEnabled) {
                return emptyList()
            }

            return evaluateThresholds(now)
        }
    }

    var lastRejectionReason: String? = null
        private set

    /**
     * Checks if a [ClassificationResult] strictly meets all conditions required to accumulate Reels time.
     */
    fun isEligibleReelsObservation(result: ClassificationResult): Boolean {
        // 1. Target application must be Instagram
        val isInstagram = result.diagnosticSummary?.isInstagramPackage == true
        if (!isInstagram) {
            lastRejectionReason = "NOT_INSTAGRAM_PACKAGE"
            logger("Observation rejected: $lastRejectionReason")
            return false
        }

        // 2. Candidate screen state must be REELS_TAB
        if (result.candidateState != CandidateScreenState.REELS_TAB) {
            lastRejectionReason = "STATE_${result.candidateState.name}"
            logger("Observation rejected: $lastRejectionReason (state=${result.candidateState}, reason=${result.reasonCode})")
            return false
        }

        // 3. Confidence must be HIGH
        if (result.confidence != Confidence.HIGH) {
            lastRejectionReason = "CONFIDENCE_${result.confidence.name}"
            logger("Observation rejected: $lastRejectionReason (requires HIGH, got ${result.confidence})")
            return false
        }

        // 4. Observation outcome must be CLASSIFIED
        val outcome = result.diagnosticSummary?.observationOutcome
        if (outcome != ObservationOutcome.CLASSIFIED) {
            lastRejectionReason = "OUTCOME_${outcome?.name ?: "NULL"}"
            logger("Observation rejected: $lastRejectionReason")
            return false
        }

        // 5. Structure must not be partial or truncated
        val diag = result.diagnosticSummary
        if (diag != null) {
            if (diag.isPartialTree) {
                lastRejectionReason = "PARTIAL_TREE"
                logger("Observation rejected: $lastRejectionReason")
                return false
            }
            if (diag.reachedNodeLimit) {
                lastRejectionReason = "REACHED_NODE_LIMIT"
                logger("Observation rejected: $lastRejectionReason")
                return false
            }
            if (diag.observationOutcome == ObservationOutcome.PARTIAL_OR_TRUNCATED) {
                lastRejectionReason = "OUTCOME_PARTIAL_OR_TRUNCATED"
                logger("Observation rejected: $lastRejectionReason")
                return false
            }
        }
        if (result.reasonCode == ReasonCode.TRUNCATED_TRAVERSAL) {
            lastRejectionReason = "REASON_TRUNCATED_TRAVERSAL"
            logger("Observation rejected: $lastRejectionReason")
            return false
        }

        // 6. Must match established positive Reels structure rules
        if (result.reasonCode !in listOf(ReasonCode.KNOWN_STRUCTURE_MATCH, ReasonCode.MULTIPLE_SIGNALS_MATCH)) {
            lastRejectionReason = "REASON_${result.reasonCode.name}"
            logger("Observation rejected: $lastRejectionReason")
            return false
        }

        lastRejectionReason = null
        logger("Observation accepted: REELS_TAB (Confidence.HIGH)")
        return true
    }

    private fun evaluateThresholds(now: Long): List<ReminderEvent> {
        val triggered = mutableListOf<ReminderEvent>()
        val currentTime = _baseAccumulatedTimeMs

        // 1. Advance warning at 9 minutes (if enabled)
        if (isAdvanceWarningEnabled && currentTime >= advanceWarningMs) {
            if (advanceWarningMs !in firedThresholds) {
                firedThresholds.add(advanceWarningMs)
                triggered.add(
                    ReminderEvent(
                        type = ReminderType.ADVANCE_WARNING,
                        thresholdMs = advanceWarningMs,
                        accumulatedTimeMs = currentTime,
                        timestampMs = now
                    )
                )
            }
        }

        // 2. First reminder at 10 minutes
        if (currentTime >= firstReminderMs) {
            if (firstReminderMs !in firedThresholds) {
                firedThresholds.add(firstReminderMs)
                triggered.add(
                    ReminderEvent(
                        type = ReminderType.FIRST_REMINDER,
                        thresholdMs = firstReminderMs,
                        accumulatedTimeMs = currentTime,
                        timestampMs = now
                    )
                )
            }
        }

        // 3. Repeat reminders every 5 minutes (15m, 20m, 25m, ...)
        if (currentTime >= firstReminderMs + repeatIntervalMs) {
            val elapsedAfterFirst = currentTime - firstReminderMs
            val intervalsPassed = elapsedAfterFirst / repeatIntervalMs
            for (i in 1..intervalsPassed) {
                val threshold = firstReminderMs + (i * repeatIntervalMs)
                if (threshold !in firedThresholds) {
                    firedThresholds.add(threshold)
                    triggered.add(
                        ReminderEvent(
                            type = ReminderType.REPEAT_REMINDER,
                            thresholdMs = threshold,
                            accumulatedTimeMs = currentTime,
                            timestampMs = now
                        )
                    )
                }
            }
        }

        return triggered
    }

    /**
     * Returns the next upcoming threshold milestone in milliseconds.
     */
    fun getNextThresholdMs(): Long {
        synchronized(lock) {
            syncState()
            val current = accumulatedReelsTimeMs
            if (isAdvanceWarningEnabled && advanceWarningMs !in firedThresholds && current < advanceWarningMs) {
                return advanceWarningMs
            }
            if (firstReminderMs !in firedThresholds && current < firstReminderMs) {
                return firstReminderMs
            }
            val elapsedAfterFirst = maxOf(0L, current - firstReminderMs)
            val intervalsPassed = elapsedAfterFirst / repeatIntervalMs
            return firstReminderMs + ((intervalsPassed + 1) * repeatIntervalMs)
        }
    }
}
