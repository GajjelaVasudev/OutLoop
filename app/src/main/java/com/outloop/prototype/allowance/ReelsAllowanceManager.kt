package com.outloop.prototype.allowance

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.outloop.classifier.CandidateScreenState
import com.outloop.classifier.ClassificationResult
import com.outloop.classifier.Confidence
import com.outloop.classifier.ObservationOutcome
import com.outloop.classifier.ReasonCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Interface abstracting local persistent storage for daily Reels allowance preferences and usage.
 * Enables fully deterministic in-memory JVM unit testing without Android framework mocks.
 */
interface AllowanceStorage {
    fun loadAllowanceEnabled(): Boolean
    fun saveAllowanceEnabled(enabled: Boolean)
    fun loadDailyLimitMinutes(): Long
    fun saveDailyLimitMinutes(minutes: Long)
    fun loadDailyAccumulatedMs(): Long
    fun saveDailyAccumulatedMs(timeMs: Long)
    fun loadStoredDate(): String?
    fun saveStoredDate(date: String)
    fun clear()
}

/**
 * SharedPreferences implementation of [AllowanceStorage] for Android production runtime.
 */
class SharedPrefsAllowanceStorage(context: Context) : AllowanceStorage {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "outloop_allowance_preferences"
        private const val KEY_ALLOWANCE_ENABLED = "key_allowance_enabled"
        private const val KEY_DAILY_LIMIT_MINUTES = "key_daily_limit_minutes"
        private const val KEY_DAILY_ACCUMULATED_MS = "key_daily_accumulated_ms"
        private const val KEY_STORED_DATE = "key_stored_date"
    }

    override fun loadAllowanceEnabled(): Boolean = prefs.getBoolean(KEY_ALLOWANCE_ENABLED, false)
    override fun saveAllowanceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOWANCE_ENABLED, enabled).apply()
    }

    override fun loadDailyLimitMinutes(): Long = prefs.getLong(KEY_DAILY_LIMIT_MINUTES, 0L)
    override fun saveDailyLimitMinutes(minutes: Long) {
        prefs.edit().putLong(KEY_DAILY_LIMIT_MINUTES, minutes).apply()
    }

    override fun loadDailyAccumulatedMs(): Long = prefs.getLong(KEY_DAILY_ACCUMULATED_MS, 0L)
    override fun saveDailyAccumulatedMs(timeMs: Long) {
        prefs.edit().putLong(KEY_DAILY_ACCUMULATED_MS, timeMs).apply()
    }

    override fun loadStoredDate(): String? = prefs.getString(KEY_STORED_DATE, null)
    override fun saveStoredDate(date: String) {
        prefs.edit().putString(KEY_STORED_DATE, date).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

/**
 * In-memory implementation of [AllowanceStorage] for JVM unit tests.
 */
class InMemoryAllowanceStorage : AllowanceStorage {
    private var enabled: Boolean = false
    private var limitMinutes: Long = 0L
    private var accumulatedMs: Long = 0L
    private var storedDate: String? = null

    override fun loadAllowanceEnabled(): Boolean = enabled
    override fun saveAllowanceEnabled(enabled: Boolean) { this.enabled = enabled }

    override fun loadDailyLimitMinutes(): Long = limitMinutes
    override fun saveDailyLimitMinutes(minutes: Long) { this.limitMinutes = minutes }

    override fun loadDailyAccumulatedMs(): Long = accumulatedMs
    override fun saveDailyAccumulatedMs(timeMs: Long) { this.accumulatedMs = timeMs }

    override fun loadStoredDate(): String? = storedDate
    override fun saveStoredDate(date: String) { this.storedDate = date }

    override fun clear() {
        enabled = false
        limitMinutes = 0L
        accumulatedMs = 0L
        storedDate = null
    }
}

/**
 * Controller and state evaluator for OutLoop's configurable daily Reels allowance.
 *
 * ARCHITECTURAL TIMING & RESTART CHARACTERISTICS:
 * 1. Monotonic intra-session tracking: Within an active boot cycle, time delta accumulation
 *    relies on an injectable monotonic clock (SystemClock.elapsedRealtime). Monotonic time is immune
 *    to NTP shifts, daylight saving changes, and manual system clock adjustments.
 * 2. Bounded observation slices: Observation intervals are capped at [staleObservationTimeoutMs]
 *    (default 10s), ensuring identical bounded-slice accounting as ReelsSessionTracker.
 * 3. Daily reset boundary: Resets are triggered deterministically when [dateProvider] produces a
 *    date string differing from the stored date (ISO-8601 "YYYY-MM-DD").
 * 4. Across device restarts:
 *    - Monotonic time resets to 0 upon reboot.
 *    - Persistent accumulated time ([AllowanceStorage]) is restored across restarts.
 *    - OutLoop anchors fresh session tracking from the first post-boot observation.
 *    - Limits of wall-clock time: If a user manually adjusts their system clock forward/backward in
 *      Android settings across days, [dateProvider] reflects the OS calendar date. OutLoop deliberately
 *      avoids unverified heuristic clock-rollback defenses to prevent false-positive lockouts.
 * 5. True opt-in defaults: Master allowance toggle and daily limits default to OFF (false / 0m).
 */
class ReelsAllowanceManager(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val dateProvider: () -> String = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            java.time.LocalDate.now().toString()
        } else {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        }
    },
    private val storage: AllowanceStorage = InMemoryAllowanceStorage(),
    val staleObservationTimeoutMs: Long = DEFAULT_STALE_TIMEOUT_MS,
    private val logger: (String) -> Unit = {}
) {

    companion object {
        const val DEFAULT_STALE_TIMEOUT_MS = 10 * 1000L // 10 seconds

        @Volatile
        private var instance: ReelsAllowanceManager? = null

        /**
         * Singleton accessor for production Android runtime.
         */
        fun getInstance(context: Context): ReelsAllowanceManager {
            return instance ?: synchronized(this) {
                instance ?: ReelsAllowanceManager(
                    storage = SharedPrefsAllowanceStorage(context.applicationContext),
                    logger = { msg -> android.util.Log.d("OutLoopAllowance", msg) }
                ).also { instance = it }
            }
        }

        /**
         * Convenience helper for routing accessibility classification results from service.
         */
        fun onClassificationResult(context: Context, result: ClassificationResult): Boolean {
            return getInstance(context).onClassificationResult(result)
        }
    }

    enum class AllowanceState {
        DISABLED,
        TRACKING_WITHIN_ALLOWANCE,
        PAUSED_WITHIN_ALLOWANCE,
        EXHAUSTED
    }

    private val lock = Any()

    private var _baseDailyAccumulatedMs: Long = 0L
    private var _storedRecordDate: String = dateProvider()
    private var lastEligibleObservationTimeMs: Long? = null
    private var isTracking: Boolean = false

    // StateFlows for UI and controllers
    private val _isAllowanceEnabled = MutableStateFlow(false)
    val isAllowanceEnabled: StateFlow<Boolean> = _isAllowanceEnabled.asStateFlow()

    private val _dailyLimitMinutes = MutableStateFlow(0L)
    val dailyLimitMinutes: StateFlow<Long> = _dailyLimitMinutes.asStateFlow()

    private val _dailyAccumulatedMs = MutableStateFlow(0L)
    val dailyAccumulatedMs: StateFlow<Long> = _dailyAccumulatedMs.asStateFlow()

    private val _allowanceState = MutableStateFlow(AllowanceState.DISABLED)
    val allowanceState: StateFlow<AllowanceState> = _allowanceState.asStateFlow()

    init {
        // Load initial state from storage
        synchronized(lock) {
            val enabled = storage.loadAllowanceEnabled()
            val limitMins = storage.loadDailyLimitMinutes()
            val savedDate = storage.loadStoredDate()
            val currentDate = dateProvider()

            _isAllowanceEnabled.value = enabled
            _dailyLimitMinutes.value = limitMins

            if (savedDate == null || savedDate != currentDate) {
                // First initialization or date rolled over while app was closed
                _storedRecordDate = currentDate
                _baseDailyAccumulatedMs = 0L
                storage.saveStoredDate(currentDate)
                storage.saveDailyAccumulatedMs(0L)
            } else {
                _storedRecordDate = savedDate
                _baseDailyAccumulatedMs = storage.loadDailyAccumulatedMs()
            }

            updateExposedFlows(clock())
        }
    }

    /**
     * Daily allowance limit in milliseconds. Returns 0 if allowance is disabled or limit is <= 0.
     */
    val dailyLimitMs: Long
        get() = synchronized(lock) {
            if (_isAllowanceEnabled.value && _dailyLimitMinutes.value > 0L) {
                _dailyLimitMinutes.value * 60 * 1000L
            } else {
                0L
            }
        }

    /**
     * Returns true if daily allowance is enabled, limit is set, and accumulated time >= limit.
     */
    val isAllowanceExhausted: Boolean
        get() {
            syncState()
            return synchronized(lock) {
                val limit = dailyLimitMs
                limit > 0L && currentTotalAccumulatedMs(clock()) >= limit
            }
        }

    /**
     * Milliseconds remaining in today's allowance, or 0 if exhausted or disabled.
     */
    val allowanceRemainingMs: Long
        get() {
            syncState()
            return synchronized(lock) {
                val limit = dailyLimitMs
                if (limit <= 0L) return@synchronized 0L
                val current = currentTotalAccumulatedMs(clock())
                maxOf(0L, limit - current)
            }
        }

    /**
     * Current total accumulated daily milliseconds including active observation interval.
     */
    val accumulatedDailyTimeMs: Long
        get() {
            syncState()
            return synchronized(lock) { currentTotalAccumulatedMs(clock()) }
        }

    private fun currentTotalAccumulatedMs(now: Long): Long {
        return if (isTracking && lastEligibleObservationTimeMs != null) {
            val activeElapsed = now - lastEligibleObservationTimeMs!!
            _baseDailyAccumulatedMs + minOf(activeElapsed, staleObservationTimeoutMs)
        } else {
            _baseDailyAccumulatedMs
        }
    }

    /**
     * Evaluates date changes, stale timeouts, and updates StateFlows.
     */
    fun syncState(now: Long = clock()) {
        synchronized(lock) {
            // 1. Check for calendar date rollover
            val currentDate = dateProvider()
            if (currentDate != _storedRecordDate) {
                logger("Daily reset triggered: date rolled over from '$_storedRecordDate' to '$currentDate'")
                _storedRecordDate = currentDate
                _baseDailyAccumulatedMs = 0L
                storage.saveStoredDate(currentDate)
                storage.saveDailyAccumulatedMs(0L)
                if (isTracking) {
                    lastEligibleObservationTimeMs = now
                }
            }

            // 2. Settle stale observation timeout
            if (isTracking && lastEligibleObservationTimeMs != null) {
                val elapsed = now - lastEligibleObservationTimeMs!!
                if (elapsed >= staleObservationTimeoutMs) {
                    _baseDailyAccumulatedMs += staleObservationTimeoutMs
                    isTracking = false
                    lastEligibleObservationTimeMs = null
                    storage.saveDailyAccumulatedMs(_baseDailyAccumulatedMs)
                    logger("Stale observation timeout expired: session paused, accumulated now ${_baseDailyAccumulatedMs}ms")
                }
            }

            updateExposedFlows(now)
        }
    }

    /**
     * Toggles the daily allowance feature on or off. Defaults to OFF.
     */
    fun setAllowanceEnabled(enabled: Boolean) {
        synchronized(lock) {
            _isAllowanceEnabled.value = enabled
            storage.saveAllowanceEnabled(enabled)
            updateExposedFlows(clock())
        }
    }

    /**
     * Configures the daily limit in minutes. 0 indicates no active limit.
     */
    fun setDailyLimitMinutes(minutes: Long) {
        synchronized(lock) {
            val sanitized = maxOf(0L, minutes)
            _dailyLimitMinutes.value = sanitized
            storage.saveDailyLimitMinutes(sanitized)
            updateExposedFlows(clock())
        }
    }

    /**
     * Explicitly resets today's accumulated time to 0.
     */
    fun resetDailyAccumulation() {
        synchronized(lock) {
            _baseDailyAccumulatedMs = 0L
            isTracking = false
            lastEligibleObservationTimeMs = null
            storage.saveDailyAccumulatedMs(0L)
            updateExposedFlows(clock())
        }
    }

    /**
     * Processes a sanitized [ClassificationResult] from the accessibility service.
     *
     * @return True if the daily allowance is exhausted and restriction is required.
     */
    fun onClassificationResult(result: ClassificationResult): Boolean {
        val now = clock()
        synchronized(lock) {
            syncState(now)

            val isEligible = isEligibleReelsObservation(result)

            if (!isEligible) {
                if (isTracking && lastEligibleObservationTimeMs != null) {
                    val elapsed = now - lastEligibleObservationTimeMs!!
                    if (elapsed > 0) {
                        _baseDailyAccumulatedMs += minOf(elapsed, staleObservationTimeoutMs)
                        storage.saveDailyAccumulatedMs(_baseDailyAccumulatedMs)
                    }
                }
                isTracking = false
                lastEligibleObservationTimeMs = null
                updateExposedFlows(now)
                return false
            }

            // Eligible Reels observation
            if (isTracking && lastEligibleObservationTimeMs != null) {
                val elapsed = now - lastEligibleObservationTimeMs!!
                if (elapsed > 0) {
                    _baseDailyAccumulatedMs += minOf(elapsed, staleObservationTimeoutMs)
                    storage.saveDailyAccumulatedMs(_baseDailyAccumulatedMs)
                }
                lastEligibleObservationTimeMs = now
            } else {
                // Resuming or beginning tracking
                isTracking = true
                lastEligibleObservationTimeMs = now
            }

            updateExposedFlows(now)

            // Return whether allowance is exhausted on confirmed Reels tab
            return isAllowanceExhausted
        }
    }

    /**
     * Reuses the verified eligibility rules from ReelsSessionTracker.
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
        val diag = result.diagnosticSummary
        if (diag != null) {
            if (diag.isPartialTree) return false
            if (diag.reachedNodeLimit) return false
            if (diag.observationOutcome == ObservationOutcome.PARTIAL_OR_TRUNCATED) return false
        }
        if (result.reasonCode == ReasonCode.TRUNCATED_TRAVERSAL) return false

        // 6. Must match established positive Reels structure rules
        if (result.reasonCode !in listOf(ReasonCode.KNOWN_STRUCTURE_MATCH, ReasonCode.MULTIPLE_SIGNALS_MATCH)) return false

        return true
    }

    private fun updateExposedFlows(now: Long) {
        val currentTotal = currentTotalAccumulatedMs(now)
        _dailyAccumulatedMs.value = currentTotal

        val limit = dailyLimitMs
        val isEnabled = _isAllowanceEnabled.value && _dailyLimitMinutes.value > 0L

        _allowanceState.value = when {
            !isEnabled -> AllowanceState.DISABLED
            currentTotal >= limit -> AllowanceState.EXHAUSTED
            isTracking -> AllowanceState.TRACKING_WITHIN_ALLOWANCE
            else -> AllowanceState.PAUSED_WITHIN_ALLOWANCE
        }
    }
}
