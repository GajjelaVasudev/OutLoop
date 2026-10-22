package com.outloop.prototype.continuous

import android.content.Context
import com.outloop.classifier.CandidateScreenState
import com.outloop.classifier.ClassificationResult
import com.outloop.classifier.Confidence
import com.outloop.classifier.ObservationOutcome
import com.outloop.classifier.ReasonCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure Kotlin manager for Continuous Reels Breaks.
 *
 * SCOPE & ARCHITECTURAL INDEPENDENCE:
 * - Operates completely independently of daily Reels allowance, extra time, and Relax Today.
 * - Uses monotonic elapsed time exclusively (SystemClock.elapsedRealtime()).
 * - Accrues continuous time based on monotonic elapsed session time from the initial confirmed Reels entry.
 * - Supports scheduled revalidation to recognize thresholds when accessibility events are sparse during video watching.
 * - Revalidates latest eligible observation and resets session if an unverified silent gap exceeds [maxAllowedObservationGapMs].
 * - Resets continuous session immediately upon transitioning to any non-Reels surface (Feed, DMs, Stories, Profiles, or external apps).
 * - Enforces single-prompt guarantee per continuous session: once completed, skipped, or dismissed,
 *   never repeatedly prompts until a fresh qualifying session begins.
 * - Thread-safe synchronization on all state transitions.
 */
class ContinuousBreakManager(
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val storage: ContinuousBreakStorage = InMemoryContinuousBreakStorage(),
    val maxAllowedObservationGapMs: Long = DEFAULT_MAX_OBSERVATION_GAP_MS,
    staleObservationTimeoutMs: Long? = null,
    private val logger: (String) -> Unit = {}
) {
    val effectiveMaxGapMs: Long = staleObservationTimeoutMs ?: maxAllowedObservationGapMs

    companion object {
        const val DEFAULT_MAX_OBSERVATION_GAP_MS = 15 * 1000L // 15 seconds max unverified gap

        @Volatile
        private var instance: ContinuousBreakManager? = null

        fun getInstance(context: Context): ContinuousBreakManager {
            return instance ?: synchronized(this) {
                instance ?: ContinuousBreakManager(
                    storage = SharedPrefsContinuousBreakStorage(context.applicationContext),
                    logger = { msg -> android.util.Log.d("OutLoopBreakDebug", msg) }
                ).also { instance = it }
            }
        }

        fun resetForTesting() {
            synchronized(this) {
                instance = null
            }
        }
    }

    private val lock = Any()

    // State flows
    private val _isBreakReminderEnabled = MutableStateFlow(storage.loadBreakReminderEnabled())
    val isBreakReminderEnabled: StateFlow<Boolean> = _isBreakReminderEnabled.asStateFlow()

    private val _breakIntervalMinutes = MutableStateFlow(storage.loadBreakIntervalMinutes())
    val breakIntervalMinutes: StateFlow<Int> = _breakIntervalMinutes.asStateFlow()

    private val _continuousAccumulatedMs = MutableStateFlow(0L)
    val continuousAccumulatedMs: StateFlow<Long> = _continuousAccumulatedMs.asStateFlow()

    private val _isPromptEligible = MutableStateFlow(false)
    val isPromptEligible: StateFlow<Boolean> = _isPromptEligible.asStateFlow()

    private val _lastClassificationState = MutableStateFlow("NONE")
    val lastClassificationState: StateFlow<String> = _lastClassificationState.asStateFlow()

    private val _lastOverlayStatus = MutableStateFlow("IDLE")
    val lastOverlayStatus: StateFlow<String> = _lastOverlayStatus.asStateFlow()

    fun updateOverlayStatus(status: String) {
        _lastOverlayStatus.value = status
    }

    // Session & Interval tracking variables
    private var isSessionActive = false
    private var sessionStartTimeMs: Long = 0L
    private var intervalBaselineTimeMs: Long = 0L
    private var lastEligibleObservationTimeMs: Long? = null
    private var hasPromptedCurrentInterval = false
    private var wasLastObservationEligible = false

    var onDismissPromptRequested: (() -> Unit)? = null
    var onSessionStateChanged: ((Boolean) -> Unit)? = null
    var onResumeScheduledCheckRequested: (() -> Unit)? = null

    val isContinuousSessionActive: Boolean
        get() = synchronized(lock) { isSessionActive }

    /**
     * Toggles Continuous Reels Break reminders.
     */
    fun setBreakReminderEnabled(enabled: Boolean) {
        synchronized(lock) {
            _isBreakReminderEnabled.value = enabled
            storage.saveBreakReminderEnabled(enabled)
            if (!enabled) {
                _isPromptEligible.value = false
                onDismissPromptRequested?.invoke()
                onSessionStateChanged?.invoke(false)
            }
            logger("Continuous break reminder enabled set to: $enabled")
        }
    }

    /**
     * Sets the continuous watching interval in minutes.
     * Validates input between [ContinuousBreakConstants.MIN_INTERVAL_MINUTES] and [ContinuousBreakConstants.MAX_INTERVAL_MINUTES].
     */
    fun setBreakIntervalMinutes(minutes: Int): Boolean {
        if (minutes < ContinuousBreakConstants.MIN_INTERVAL_MINUTES ||
            minutes > ContinuousBreakConstants.MAX_INTERVAL_MINUTES) {
            logger("Rejected invalid break interval: $minutes minutes")
            return false
        }
        synchronized(lock) {
            _breakIntervalMinutes.value = minutes
            storage.saveBreakIntervalMinutes(minutes)
            logger("Continuous break interval updated to: $minutes minutes")

            // Safe future eligibility re-evaluation without duplicate prompts
            if (isSessionActive && !hasPromptedCurrentInterval && _isBreakReminderEnabled.value) {
                val targetMs = minutes * 60 * 1000L
                val elapsed = maxOf(0L, clock() - intervalBaselineTimeMs)
                _continuousAccumulatedMs.value = elapsed
                if (elapsed >= targetMs) {
                    hasPromptedCurrentInterval = true
                    _isPromptEligible.value = true
                    logger("Threshold reached on interval change to ${minutes}m (elapsed=${elapsed}ms)")
                }
            }
            return true
        }
    }

    /**
     * Resets the baseline time for the current continuous break interval to [now].
     * Resumes continuous tracking for the next prompt without requiring the user to leave Reels.
     */
    fun resetIntervalBaseline(now: Long = clock()) {
        synchronized(lock) {
            intervalBaselineTimeMs = now
            lastEligibleObservationTimeMs = now
            hasPromptedCurrentInterval = false
            _isPromptEligible.value = false
            _continuousAccumulatedMs.value = 0L
            logger("Continuous break interval baseline reset to monotonic time: $now")
            onResumeScheduledCheckRequested?.invoke()
        }
    }

    /**
     * Evaluates a sanitized [ClassificationResult] from the accessibility service.
     *
     * @return True if a break prompt should be shown immediately.
     */
    fun onClassificationResult(result: ClassificationResult): Boolean {
        val now = clock()
        synchronized(lock) {
            _lastClassificationState.value = "${result.candidateState.name} (${result.confidence.name})"
            val eligible = isEligibleReelsObservation(result)

            if (!eligible) {
                wasLastObservationEligible = false
                // If user definitively left Reels (e.g. to Feed, Stories, DMs, or other app), reset session immediately
                if (isDefinitiveExitFromReels(result)) {
                    resetSessionInternal("Definitive exit from Reels: ${result.candidateState.name}")
                    return false
                }

                // For transient/unknown observations (e.g. UNKNOWN, missing root, loading), do not reset immediately.
                // Reset only if the unverified gap exceeds effectiveMaxGapMs.
                if (isSessionActive) {
                    val lastSeen = lastEligibleObservationTimeMs ?: intervalBaselineTimeMs
                    val gap = now - lastSeen
                    if (gap > effectiveMaxGapMs) {
                        resetSessionInternal("Observation gap (${gap}ms) exceeded max allowed gap (${effectiveMaxGapMs}ms)")
                    }
                }
                return false
            }

            // Confirmed Reels screen
            val returningToReels = !wasLastObservationEligible
            wasLastObservationEligible = true

            if (!isSessionActive) {
                isSessionActive = true
                sessionStartTimeMs = now
                intervalBaselineTimeMs = now
                lastEligibleObservationTimeMs = now
                hasPromptedCurrentInterval = false
                _continuousAccumulatedMs.value = 0L
                _isPromptEligible.value = false
                logger("Continuous Reels session started at monotonic time: $now (interval=${_breakIntervalMinutes.value}m)")
                onSessionStateChanged?.invoke(true)
            } else {
                val lastSeen = lastEligibleObservationTimeMs ?: now
                val gap = now - lastSeen
                if (gap > effectiveMaxGapMs) {
                    // Unverified gap exceeded timeout (e.g. phone was asleep or suspended)
                    resetSessionInternal("Observation gap (${gap}ms) exceeded max allowed gap (${effectiveMaxGapMs}ms)")
                    return false
                }
                lastEligibleObservationTimeMs = now
                val elapsed = maxOf(0L, now - intervalBaselineTimeMs)
                _continuousAccumulatedMs.value = elapsed
                logger("Reels observation accepted. Interval elapsed: ${elapsed / 1000}s / ${_breakIntervalMinutes.value * 60}s")
            }

            // Check prompt eligibility for the current interval
            val targetMs = _breakIntervalMinutes.value * 60 * 1000L
            val elapsed = maxOf(0L, now - intervalBaselineTimeMs)
            if (_isBreakReminderEnabled.value) {
                // Restore pending prompt if returning from a temporary departure (e.g. Recent Apps / app switch)
                if (_isPromptEligible.value && returningToReels) {
                    logger("Restoring pending break prompt upon returning to Reels (${_breakIntervalMinutes.value}m)")
                    return true
                }
                if (elapsed >= targetMs && !hasPromptedCurrentInterval) {
                    hasPromptedCurrentInterval = true
                    _isPromptEligible.value = true
                    logger("Continuous Reels break threshold reached (${_breakIntervalMinutes.value}m). Break prompt eligible.")
                    return true
                }
            }

            return false
        }
    }

    /**
     * Revalidates latest observation time during scheduled periodic checks while on Reels.
     */
    fun updateLatestObservationTime(now: Long = clock()) {
        synchronized(lock) {
            if (isSessionActive) {
                lastEligibleObservationTimeMs = now
                val elapsed = maxOf(0L, now - intervalBaselineTimeMs)
                _continuousAccumulatedMs.value = elapsed
            }
        }
    }

    /**
     * Checks if the break threshold has been reached (used by the 1-second scheduled check).
     *
     * @return True if break prompt should be shown immediately.
     */
    fun checkThresholdReached(now: Long = clock()): Boolean {
        synchronized(lock) {
            if (!isSessionActive || !_isBreakReminderEnabled.value || hasPromptedCurrentInterval) {
                return false
            }
            val elapsed = maxOf(0L, now - intervalBaselineTimeMs)
            _continuousAccumulatedMs.value = elapsed
            val targetMs = _breakIntervalMinutes.value * 60 * 1000L
            if (elapsed >= targetMs) {
                hasPromptedCurrentInterval = true
                _isPromptEligible.value = true
                logger("Continuous Reels break threshold reached via scheduled check (${_breakIntervalMinutes.value}m). Elapsed: ${elapsed}ms")
                return true
            }
            return false
        }
    }

    /**
     * Returns current continuous elapsed watching time in the current break interval in milliseconds.
     */
    fun getCurrentContinuousElapsedMs(now: Long = clock()): Long {
        synchronized(lock) {
            if (!isSessionActive) return 0L
            return maxOf(0L, now - intervalBaselineTimeMs)
        }
    }

    /**
     * Called when the user completes a Mind Refresh activity.
     * Resets the interval baseline to current monotonic time and resumes continuous monitoring.
     */
    fun onMindRefreshCompleted() {
        val now = clock()
        resetIntervalBaseline(now)
        logger("Mind Refresh completed. Baseline reset to $now. Continuous monitoring resumed.")
    }

    /**
     * Called when the user skips a Mind Refresh activity.
     * Resets the interval baseline to current monotonic time and resumes continuous monitoring.
     */
    fun onMindRefreshSkipped() {
        val now = clock()
        resetIntervalBaseline(now)
        logger("Mind Refresh skipped. Baseline reset to $now. Continuous monitoring resumed.")
    }

    /**
     * Called when the user chooses "Take a break" on the attention check prompt.
     * Resets the interval baseline and resumes continuous monitoring for the next interval.
     */
    fun onBreakTaken() {
        val now = clock()
        resetIntervalBaseline(now)
        logger("Break taken. Baseline reset to $now. Continuous monitoring resumed.")
    }

    /**
     * Called when the user chooses "Continue watching" on the attention check prompt.
     * Dismisses the prompt immediately without launching an activity, resets the interval baseline,
     * and resumes continuous monitoring for the next interval.
     */
    fun onContinueWatching() {
        val now = clock()
        resetIntervalBaseline(now)
        logger("Continue watching selected. Baseline reset to $now. Continuous monitoring resumed.")
    }

    /**
     * Compatibility alias for completing/taking a break.
     */
    fun onBreakCompleted() = onMindRefreshCompleted()

    /**
     * Compatibility alias for skipping prompt.
     */
    fun onBreakSkipped() = onMindRefreshSkipped()

    /**
     * Compatibility alias for dismissing prompt.
     */
    fun onBreakDismissed() = onContinueWatching()

    /**
     * Resets the continuous session (e.g. when leaving Instagram or disconnecting).
     */
    fun resetSession(reason: String = "Manual reset") {
        synchronized(lock) {
            resetSessionInternal(reason)
        }
    }

    private fun resetSessionInternal(reason: String) {
        if (isSessionActive || _continuousAccumulatedMs.value > 0L || _isPromptEligible.value) {
            logger("Continuous Reels session ended and reset. Reason: $reason")
        }
        val wasActive = isSessionActive
        isSessionActive = false
        sessionStartTimeMs = 0L
        intervalBaselineTimeMs = 0L
        lastEligibleObservationTimeMs = null
        hasPromptedCurrentInterval = false
        wasLastObservationEligible = false
        _continuousAccumulatedMs.value = 0L
        _isPromptEligible.value = false
        onDismissPromptRequested?.invoke()
        if (wasActive) {
            onSessionStateChanged?.invoke(false)
        }
    }

    /**
     * Checks if [result] is a confident, non-partial Instagram Reels observation.
     */
    fun isEligibleReelsObservation(result: ClassificationResult): Boolean {
        // 1. Target application must be Instagram
        val isInstagram = result.diagnosticSummary?.isInstagramPackage == true
        if (!isInstagram) return false

        // 2. Candidate screen state must be REELS_TAB
        if (result.candidateState != CandidateScreenState.REELS_TAB) return false

        // 3. Confidence must be HIGH
        if (result.confidence != Confidence.HIGH) return false

        // 4. Observation outcome must be CLASSIFIED
        val outcome = result.diagnosticSummary?.observationOutcome
        if (outcome != ObservationOutcome.CLASSIFIED) return false

        // 5. Structure must not be partial or truncated
        if (result.diagnosticSummary?.isPartialTree == true) return false

        return true
    }

    /**
     * Identifies whether [result] represents an intentional and definitive departure from Reels
     * (e.g. leaving Instagram app, or navigating to Feed, Stories, DMs, or Profile).
     */
    fun isDefinitiveExitFromReels(result: ClassificationResult): Boolean {
        // 1. Outside Instagram package
        val isInstagram = result.diagnosticSummary?.isInstagramPackage == true
        if (!isInstagram) return true

        // 2. Explicitly confirmed other Instagram surfaces
        if (result.candidateState in listOf(
                CandidateScreenState.HOME_FEED,
                CandidateScreenState.STORIES,
                CandidateScreenState.DM_CONVERSATION,
                CandidateScreenState.DM_INBOX,
                CandidateScreenState.PROFILE
            )
        ) {
            return true
        }

        // 3. Instagram explicitly not foreground
        if (result.reasonCode == ReasonCode.APP_NOT_FOREGROUND) return true

        return false
    }
}
