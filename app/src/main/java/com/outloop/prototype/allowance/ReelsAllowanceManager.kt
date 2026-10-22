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
 * Standard preset options for requesting extra Reels allowance.
 */
object SpecialDayPresets {
    val EXTRA_MINUTES_PRESETS = listOf(10L, 15L, 30L)
    val NORMAL_LIMIT_PRESETS = listOf(15L, 30L, 45L, 60L)
}

/**
 * Predefined non-judgmental reasons for requesting extra allowance or relaxing restrictions.
 * These reasons serve solely as user context/reflection and NEVER affect calculations or eligibility.
 */
object SpecialDayReasons {
    const val HOLIDAY = "Holiday or festival"
    const val VACATION = "Vacation"
    const val DAY_OFF = "Unexpected day off"
    const val CELEBRATION = "Celebration or special occasion"
    const val PERSONAL = "Personal reason"

    val PRESETS = listOf(
        HOLIDAY,
        VACATION,
        DAY_OFF,
        CELEBRATION,
        PERSONAL
    )
}

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
    fun loadExtraAllowanceMinutes(): Long
    fun saveExtraAllowanceMinutes(minutes: Long)
    fun loadExtraAllowanceHistory(): List<Long>
    fun saveExtraAllowanceHistory(history: List<Long>)
    fun loadRestrictionRelaxed(): Boolean
    fun saveRestrictionRelaxed(relaxed: Boolean)
    fun loadSpecialDayReason(): String?
    fun saveSpecialDayReason(reason: String?)

    // Clock integrity & anti-tampering persistence
    fun loadLastWallClockMs(): Long
    fun saveLastWallClockMs(timeMs: Long)
    fun loadLastElapsedRealtimeMs(): Long
    fun saveLastElapsedRealtimeMs(timeMs: Long)
    fun loadLastBootCount(): Int
    fun saveLastBootCount(count: Int)
    fun loadDayStartWallClockMs(): Long
    fun saveDayStartWallClockMs(timeMs: Long)
    fun loadClockDiscrepancyDetected(): Boolean
    fun saveClockDiscrepancyDetected(detected: Boolean)
    fun loadDiscrepancyReason(): String?
    fun saveDiscrepancyReason(reason: String?)
    fun loadLastTimeZone(): String?
    fun saveLastTimeZone(zoneId: String)

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
        private const val KEY_EXTRA_ALLOWANCE_MINUTES = "key_extra_allowance_minutes"
        private const val KEY_EXTRA_ALLOWANCE_HISTORY = "key_extra_allowance_history"
        private const val KEY_RESTRICTION_RELAXED = "key_restriction_relaxed"
        private const val KEY_SPECIAL_DAY_REASON = "key_special_day_reason"
        private const val KEY_LAST_WALL_CLOCK_MS = "key_last_wall_clock_ms"
        private const val KEY_LAST_ELAPSED_REALTIME_MS = "key_last_elapsed_realtime_ms"
        private const val KEY_LAST_BOOT_COUNT = "key_last_boot_count"
        private const val KEY_DAY_START_WALL_CLOCK_MS = "key_day_start_wall_clock_ms"
        private const val KEY_CLOCK_DISCREPANCY_DETECTED = "key_clock_discrepancy_detected"
        private const val KEY_DISCREPANCY_REASON = "key_discrepancy_reason"
        private const val KEY_LAST_TIME_ZONE = "key_last_time_zone"
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

    override fun loadExtraAllowanceMinutes(): Long = prefs.getLong(KEY_EXTRA_ALLOWANCE_MINUTES, 0L)
    override fun saveExtraAllowanceMinutes(minutes: Long) {
        prefs.edit().putLong(KEY_EXTRA_ALLOWANCE_MINUTES, minutes).apply()
    }

    override fun loadExtraAllowanceHistory(): List<Long> {
        val raw = prefs.getString(KEY_EXTRA_ALLOWANCE_HISTORY, null)
        if (!raw.isNullOrBlank()) {
            return raw.split(",")
                .mapNotNull { it.trim().toLongOrNull() }
                .filter { it > 0L }
        }
        val currentExtra = loadExtraAllowanceMinutes()
        return if (currentExtra > 0L) listOf(currentExtra) else emptyList()
    }

    override fun saveExtraAllowanceHistory(history: List<Long>) {
        if (history.isEmpty()) {
            prefs.edit().remove(KEY_EXTRA_ALLOWANCE_HISTORY).apply()
        } else {
            prefs.edit().putString(KEY_EXTRA_ALLOWANCE_HISTORY, history.joinToString(",")).apply()
        }
    }

    override fun loadRestrictionRelaxed(): Boolean = prefs.getBoolean(KEY_RESTRICTION_RELAXED, false)
    override fun saveRestrictionRelaxed(relaxed: Boolean) {
        prefs.edit().putBoolean(KEY_RESTRICTION_RELAXED, relaxed).apply()
    }

    override fun loadSpecialDayReason(): String? = prefs.getString(KEY_SPECIAL_DAY_REASON, null)
    override fun saveSpecialDayReason(reason: String?) {
        prefs.edit().putString(KEY_SPECIAL_DAY_REASON, reason).apply()
    }

    override fun loadLastWallClockMs(): Long = prefs.getLong(KEY_LAST_WALL_CLOCK_MS, 0L)
    override fun saveLastWallClockMs(timeMs: Long) {
        prefs.edit().putLong(KEY_LAST_WALL_CLOCK_MS, timeMs).apply()
    }

    override fun loadLastElapsedRealtimeMs(): Long = prefs.getLong(KEY_LAST_ELAPSED_REALTIME_MS, 0L)
    override fun saveLastElapsedRealtimeMs(timeMs: Long) {
        prefs.edit().putLong(KEY_LAST_ELAPSED_REALTIME_MS, timeMs).apply()
    }

    override fun loadLastBootCount(): Int = prefs.getInt(KEY_LAST_BOOT_COUNT, -1)
    override fun saveLastBootCount(count: Int) {
        prefs.edit().putInt(KEY_LAST_BOOT_COUNT, count).apply()
    }

    override fun loadDayStartWallClockMs(): Long = prefs.getLong(KEY_DAY_START_WALL_CLOCK_MS, 0L)
    override fun saveDayStartWallClockMs(timeMs: Long) {
        prefs.edit().putLong(KEY_DAY_START_WALL_CLOCK_MS, timeMs).apply()
    }

    override fun loadClockDiscrepancyDetected(): Boolean = prefs.getBoolean(KEY_CLOCK_DISCREPANCY_DETECTED, false)
    override fun saveClockDiscrepancyDetected(detected: Boolean) {
        prefs.edit().putBoolean(KEY_CLOCK_DISCREPANCY_DETECTED, detected).apply()
    }

    override fun loadDiscrepancyReason(): String? = prefs.getString(KEY_DISCREPANCY_REASON, null)
    override fun saveDiscrepancyReason(reason: String?) {
        prefs.edit().putString(KEY_DISCREPANCY_REASON, reason).apply()
    }

    override fun loadLastTimeZone(): String? = prefs.getString(KEY_LAST_TIME_ZONE, null)
    override fun saveLastTimeZone(zoneId: String) {
        prefs.edit().putString(KEY_LAST_TIME_ZONE, zoneId).apply()
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
    private var extraMinutes: Long = 0L
    private var extraHistory: List<Long> = emptyList()
    private var relaxed: Boolean = false
    private var reason: String? = null
    private var lastWallClock: Long = 0L
    private var lastElapsedRealtime: Long = 0L
    private var lastBootCount: Int = -1
    private var dayStartWallClock: Long = 0L
    private var clockDiscrepancyDetected: Boolean = false
    private var discrepancyReason: String? = null
    private var lastTimeZone: String? = null

    override fun loadAllowanceEnabled(): Boolean = enabled
    override fun saveAllowanceEnabled(enabled: Boolean) { this.enabled = enabled }

    override fun loadDailyLimitMinutes(): Long = limitMinutes
    override fun saveDailyLimitMinutes(minutes: Long) { this.limitMinutes = minutes }

    override fun loadDailyAccumulatedMs(): Long = accumulatedMs
    override fun saveDailyAccumulatedMs(timeMs: Long) { this.accumulatedMs = timeMs }

    override fun loadStoredDate(): String? = storedDate
    override fun saveStoredDate(date: String) { this.storedDate = date }

    override fun loadExtraAllowanceMinutes(): Long = extraMinutes
    override fun saveExtraAllowanceMinutes(minutes: Long) { this.extraMinutes = minutes }

    override fun loadExtraAllowanceHistory(): List<Long> {
        if (extraHistory.isNotEmpty()) return extraHistory
        return if (extraMinutes > 0L) listOf(extraMinutes) else emptyList()
    }

    override fun saveExtraAllowanceHistory(history: List<Long>) {
        this.extraHistory = history
    }

    override fun loadRestrictionRelaxed(): Boolean = relaxed
    override fun saveRestrictionRelaxed(relaxed: Boolean) { this.relaxed = relaxed }

    override fun loadSpecialDayReason(): String? = reason
    override fun saveSpecialDayReason(reason: String?) { this.reason = reason }

    override fun loadLastWallClockMs(): Long = lastWallClock
    override fun saveLastWallClockMs(timeMs: Long) { this.lastWallClock = timeMs }

    override fun loadLastElapsedRealtimeMs(): Long = lastElapsedRealtime
    override fun saveLastElapsedRealtimeMs(timeMs: Long) { this.lastElapsedRealtime = timeMs }

    override fun loadLastBootCount(): Int = lastBootCount
    override fun saveLastBootCount(count: Int) { this.lastBootCount = count }

    override fun loadDayStartWallClockMs(): Long = dayStartWallClock
    override fun saveDayStartWallClockMs(timeMs: Long) { this.dayStartWallClock = timeMs }

    override fun loadClockDiscrepancyDetected(): Boolean = clockDiscrepancyDetected
    override fun saveClockDiscrepancyDetected(detected: Boolean) { this.clockDiscrepancyDetected = detected }

    override fun loadDiscrepancyReason(): String? = discrepancyReason
    override fun saveDiscrepancyReason(reason: String?) { this.discrepancyReason = reason }

    override fun loadLastTimeZone(): String? = lastTimeZone
    override fun saveLastTimeZone(zoneId: String) { this.lastTimeZone = zoneId }

    override fun clear() {
        enabled = false
        limitMinutes = 0L
        accumulatedMs = 0L
        storedDate = null
        extraMinutes = 0L
        extraHistory = emptyList()
        relaxed = false
        reason = null
        lastWallClock = 0L
        lastElapsedRealtime = 0L
        lastBootCount = -1
        dayStartWallClock = 0L
        clockDiscrepancyDetected = false
        discrepancyReason = null
        lastTimeZone = null
    }
}

/**
 * Controller and state evaluator for OutLoop's configurable daily Reels allowance and Special-Day flexibility.
 *
 * ARCHITECTURAL TIMING, PERSISTENCE & SPECIAL-DAY CHARACTERISTICS:
 * 1. Monotonic intra-session tracking: Within an active boot cycle, time delta accumulation
 *    relies on an injectable monotonic clock (SystemClock.elapsedRealtime). Monotonic time is immune
 *    to NTP shifts, daylight saving changes, and manual system clock adjustments.
 * 2. Bounded observation slices: Observation intervals are capped at [staleObservationTimeoutMs]
 *    (default 10s), ensuring identical bounded-slice accounting as ReelsSessionTracker.
 * 3. Daily reset boundary & Special-Day expiration: Resets are triggered deterministically when
 *    [dateProvider] produces a date string differing from the stored date (ISO-8601 "YYYY-MM-DD").
 *    Upon midnight rollover:
 *    - Previous day's extra allowance minutes and temporary relaxation state are automatically cleared.
 *    - The user's normal configured limit and enabled toggle are strictly preserved.
 *    - Fresh daily usage accounting starts at 0ms.
 * 4. Across device restarts:
 *    - Monotonic time resets to 0 upon reboot.
 *    - Persistent accumulated time, extra allowance, and relaxation states ([AllowanceStorage])
 *      are restored across restarts on the same local calendar day.
 *    - OutLoop anchors fresh session tracking from the first post-boot observation.
 * 5. Limitations of wall-clock time & manual clock adjustments:
 *    - If a user manually changes the device's system date or time zone forward or backward in Android
 *      settings across calendar days, [dateProvider] reflects the OS calendar date, which triggers a daily rollover.
 *    - OutLoop deliberately avoids unverified heuristic clock-rollback defenses to prevent false-positive lockouts.
 *      OutLoop does not claim tamper-proof behavior against intentional manual system date manipulation.
 * 6. Non-judgmental Special-Day reasons:
 *    - Reason strings are optional context for user reflection only; reasons are never scored, verified,
 *      rejected, or factored into mathematical allowance or restriction logic.
 * 7. True opt-in defaults: Master allowance toggle and daily limits default to OFF (false / 0m).
 */
class ReelsAllowanceManager(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long = { System.currentTimeMillis() },
    private val dateProvider: () -> String = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            java.time.LocalDate.now().toString()
        } else {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        }
    },
    private val bootCountProvider: () -> Int = { -1 },
    private val timeZoneProvider: () -> String = { java.util.TimeZone.getDefault().id },
    private val storage: AllowanceStorage = InMemoryAllowanceStorage(),
    private val historyRepository: com.outloop.prototype.history.AttentionHistoryRepository? = null,
    val staleObservationTimeoutMs: Long = DEFAULT_STALE_TIMEOUT_MS,
    private val logger: (String) -> Unit = {}
) {

    companion object {
        const val DEFAULT_STALE_TIMEOUT_MS = 10 * 1000L // 10 seconds
        const val DUPLICATE_ACTION_DEBOUNCE_MS = 500L

        @Volatile
        private var instance: ReelsAllowanceManager? = null

        /**
         * Singleton accessor for production Android runtime.
         */
        fun getInstance(context: Context): ReelsAllowanceManager {
            return instance ?: synchronized(this) {
                instance ?: ReelsAllowanceManager(
                    bootCountProvider = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            try {
                                android.provider.Settings.Global.getInt(
                                    context.contentResolver,
                                    android.provider.Settings.Global.BOOT_COUNT,
                                    -1
                                )
                            } catch (_: Exception) {
                                -1
                            }
                        } else {
                            -1
                        }
                    },
                    timeZoneProvider = { java.util.TimeZone.getDefault().id },
                    storage = SharedPrefsAllowanceStorage(context.applicationContext),
                    historyRepository = com.outloop.prototype.history.AttentionHistoryRepository.getInstance(context.applicationContext),
                    logger = { msg -> android.util.Log.d("OutLoopAllowance", msg) }
                ).also { instance = it }
            }
        }

        fun resetForTesting() {
            synchronized(this) {
                instance = null
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
        EXHAUSTED,
        RELAXED
    }

    private val lock = Any()

    private var _baseDailyAccumulatedMs: Long = 0L
    private var _storedRecordDate: String = dateProvider()
    private var lastEligibleObservationTimeMs: Long? = null
    private var isTracking: Boolean = false
    private var lastGrantTimeMs: Long = 0L

    // Clock integrity anchors
    private var _lastWallClockMs: Long = 0L
    private var _lastElapsedRealtimeMs: Long = 0L
    private var _lastBootCount: Int = -1
    private var _dayStartWallClockMs: Long = 0L
    private var _lastTimeZone: String = ""

    var onRestrictionDismissRequested: (() -> Unit)? = null

    // StateFlows for UI and controllers
    private val _isAllowanceEnabled = MutableStateFlow(false)
    val isAllowanceEnabled: StateFlow<Boolean> = _isAllowanceEnabled.asStateFlow()

    private val _dailyLimitMinutes = MutableStateFlow(0L)
    val dailyLimitMinutes: StateFlow<Long> = _dailyLimitMinutes.asStateFlow()

    private val _extraAllowanceMinutes = MutableStateFlow(0L)
    val extraAllowanceMinutes: StateFlow<Long> = _extraAllowanceMinutes.asStateFlow()

    private var _extraAllowanceHistory: MutableList<Long> = mutableListOf()
    private val _extraHistoryFlow = MutableStateFlow<List<Long>>(emptyList())
    val extraAllowanceHistory: StateFlow<List<Long>> = _extraHistoryFlow.asStateFlow()

    private val _effectiveLimitMinutes = MutableStateFlow(0L)
    val effectiveLimitMinutes: StateFlow<Long> = _effectiveLimitMinutes.asStateFlow()

    private val _isRestrictionRelaxed = MutableStateFlow(false)
    val isRestrictionRelaxed: StateFlow<Boolean> = _isRestrictionRelaxed.asStateFlow()

    private val _specialDayReason = MutableStateFlow<String?>(null)
    val specialDayReason: StateFlow<String?> = _specialDayReason.asStateFlow()

    private val _dailyAccumulatedMs = MutableStateFlow(0L)
    val dailyAccumulatedMs: StateFlow<Long> = _dailyAccumulatedMs.asStateFlow()

    private val _allowanceState = MutableStateFlow(AllowanceState.DISABLED)
    val allowanceState: StateFlow<AllowanceState> = _allowanceState.asStateFlow()

    private val _isClockDiscrepancyDetected = MutableStateFlow(false)
    val isClockDiscrepancyDetected: StateFlow<Boolean> = _isClockDiscrepancyDetected.asStateFlow()

    private val _discrepancyReason = MutableStateFlow<String?>(null)
    val discrepancyReason: StateFlow<String?> = _discrepancyReason.asStateFlow()

    init {
        // Load initial state from storage
        synchronized(lock) {
            val enabled = storage.loadAllowanceEnabled()
            val limitMins = storage.loadDailyLimitMinutes()
            val savedDate = storage.loadStoredDate()
            val currentDate = dateProvider()

            _isAllowanceEnabled.value = enabled
            _dailyLimitMinutes.value = limitMins
            _lastWallClockMs = storage.loadLastWallClockMs()
            _lastElapsedRealtimeMs = storage.loadLastElapsedRealtimeMs()
            _lastBootCount = storage.loadLastBootCount()
            _dayStartWallClockMs = storage.loadDayStartWallClockMs()
            _lastTimeZone = storage.loadLastTimeZone() ?: timeZoneProvider()

            val savedDiscrepancy = storage.loadClockDiscrepancyDetected()
            val savedReason = storage.loadDiscrepancyReason()
            _isClockDiscrepancyDetected.value = savedDiscrepancy
            _discrepancyReason.value = savedReason

            val nowMono = clock()
            val nowWall = wallClock()

            // Initialize timing anchors if not set
            if (_lastWallClockMs == 0L) {
                _lastWallClockMs = nowWall
                storage.saveLastWallClockMs(nowWall)
            }
            if (_lastElapsedRealtimeMs == 0L) {
                _lastElapsedRealtimeMs = nowMono
                storage.saveLastElapsedRealtimeMs(nowMono)
            }
            if (_dayStartWallClockMs == 0L) {
                _dayStartWallClockMs = nowWall
                storage.saveDayStartWallClockMs(nowWall)
            }
            if (_lastTimeZone.isBlank()) {
                _lastTimeZone = timeZoneProvider()
                storage.saveLastTimeZone(_lastTimeZone)
            }

            if (savedDate == null || savedDate != currentDate) {
                val canRollover = !savedDiscrepancy && (_lastWallClockMs == 0L || nowWall >= _lastWallClockMs)
                if (canRollover || savedDate == null) {
                    _storedRecordDate = currentDate
                    _baseDailyAccumulatedMs = 0L
                    _extraAllowanceMinutes.value = 0L
                    _extraAllowanceHistory = mutableListOf()
                    _extraHistoryFlow.value = emptyList()
                    _isRestrictionRelaxed.value = false
                    _specialDayReason.value = null
                    _dayStartWallClockMs = nowWall
                    storage.saveStoredDate(currentDate)
                    storage.saveDailyAccumulatedMs(0L)
                    storage.saveExtraAllowanceMinutes(0L)
                    storage.saveExtraAllowanceHistory(emptyList())
                    storage.saveRestrictionRelaxed(false)
                    storage.saveSpecialDayReason(null)
                    storage.saveDayStartWallClockMs(nowWall)
                } else {
                    _storedRecordDate = savedDate
                    _baseDailyAccumulatedMs = storage.loadDailyAccumulatedMs()
                    _extraAllowanceMinutes.value = storage.loadExtraAllowanceMinutes()
                    _extraAllowanceHistory = storage.loadExtraAllowanceHistory().toMutableList()
                    _extraHistoryFlow.value = _extraAllowanceHistory.toList()
                    _isRestrictionRelaxed.value = storage.loadRestrictionRelaxed()
                    _specialDayReason.value = storage.loadSpecialDayReason()
                }
            } else {
                _storedRecordDate = savedDate
                _baseDailyAccumulatedMs = storage.loadDailyAccumulatedMs()
                _extraAllowanceMinutes.value = storage.loadExtraAllowanceMinutes()
                _extraAllowanceHistory = storage.loadExtraAllowanceHistory().toMutableList()
                _extraHistoryFlow.value = _extraAllowanceHistory.toList()
                _isRestrictionRelaxed.value = storage.loadRestrictionRelaxed()
                _specialDayReason.value = storage.loadSpecialDayReason()
            }

            updateExposedFlows(nowMono)
        }
    }

    /**
     * Normal daily allowance limit in milliseconds. Returns 0 if allowance is disabled or limit is <= 0.
     * Preserved for backward compatibility.
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
     * Effective allowance limit in milliseconds (normal allowance + extra allowance).
     * Returns 0 if allowance is disabled or effective minutes <= 0.
     */
    val effectiveLimitMs: Long
        get() = synchronized(lock) {
            if (_isAllowanceEnabled.value) {
                val totalMinutes = _dailyLimitMinutes.value + _extraAllowanceMinutes.value
                if (totalMinutes > 0L) totalMinutes * 60 * 1000L else 0L
            } else {
                0L
            }
        }

    /**
     * Internal exhaustion evaluator without calling [syncState], avoiding re-entrant recursion.
     */
    private fun isExhaustedInternal(now: Long = clock()): Boolean {
        if (!_isAllowanceEnabled.value) return false
        if (_isRestrictionRelaxed.value) return false
        val limit = effectiveLimitMs
        return limit > 0L && currentTotalAccumulatedMs(now) >= limit
    }

    /**
     * Returns true if daily allowance is enabled, not temporarily relaxed, effective limit is set,
     * and accumulated daily time >= effective limit.
     */
    val isAllowanceExhausted: Boolean
        get() {
            syncState()
            return synchronized(lock) {
                isExhaustedInternal()
            }
        }

    /**
     * Milliseconds remaining in today's effective allowance, or 0 if exhausted or disabled.
     * Guaranteed never to return a negative value.
     */
    val allowanceRemainingMs: Long
        get() {
            syncState()
            return synchronized(lock) {
                val limit = effectiveLimitMs
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
        val raw = if (isTracking && lastEligibleObservationTimeMs != null) {
            val activeElapsed = maxOf(0L, now - lastEligibleObservationTimeMs!!)
            _baseDailyAccumulatedMs + minOf(activeElapsed, staleObservationTimeoutMs)
        } else {
            _baseDailyAccumulatedMs
        }
        val limit = effectiveLimitMs
        return if (_isAllowanceEnabled.value && !_isRestrictionRelaxed.value && limit > 0L) {
            minOf(raw, limit)
        } else {
            raw
        }
    }

    /**
     * Evaluates clock tampering, date changes, stale timeouts, and updates StateFlows.
     */
    fun syncState(nowMono: Long = clock(), nowWall: Long = wallClock()) {
        synchronized(lock) {
            val currentBootCount = bootCountProvider()
            val isReboot = if (currentBootCount != -1 && _lastBootCount != -1) {
                currentBootCount > _lastBootCount
            } else {
                _lastElapsedRealtimeMs > 0L && nowMono < _lastElapsedRealtimeMs
            }

            if (isReboot) {
                isTracking = false
                lastEligibleObservationTimeMs = null
            }

            val currentTimeZone = timeZoneProvider()
            val isTimeZoneChanged = _lastTimeZone.isNotBlank() && currentTimeZone != _lastTimeZone

            var discrepancyFound = false
            var discrepancyMsg: String? = null

            if (_lastWallClockMs > 0L) {
                val deltaWall = nowWall - _lastWallClockMs
                val deltaMono = if (isReboot) nowMono else (nowMono - _lastElapsedRealtimeMs)

                if (deltaWall < -15_000L) {
                    // Backward clock jump detected (e.g. rolled back across midnight or hours)
                    discrepancyFound = true
                    discrepancyMsg = "System clock moved backward by ${(-deltaWall) / 1000}s"
                } else if (!isReboot && (deltaWall - deltaMono) > 60_000L) {
                    // Forward clock jump during same boot session (e.g. user jumped hours or days)
                    discrepancyFound = true
                    discrepancyMsg = "Wall clock jumped forward by ${(deltaWall - deltaMono) / 1000}s ahead of elapsed time"
                } else if (isReboot && deltaWall < 0L) {
                    // Clock earlier than before reboot
                    discrepancyFound = true
                    discrepancyMsg = "Wall clock across reboot earlier than last recorded session"
                } else if (isTimeZoneChanged && dateProvider() != _storedRecordDate) {
                    // Timezone changed across date boundary
                    discrepancyFound = true
                    discrepancyMsg = "Timezone changed from '$_lastTimeZone' to '$currentTimeZone' across date boundary"
                }
            }

            // Recovery check
            if (_isClockDiscrepancyDetected.value) {
                val currentDate = dateProvider()
                if (!discrepancyFound) {
                    // If the clock has returned to normal or advanced past the previous wall clock anchor
                    if (nowWall >= _lastWallClockMs && currentDate == _storedRecordDate) {
                        _isClockDiscrepancyDetected.value = false
                        _discrepancyReason.value = null
                        storage.saveClockDiscrepancyDetected(false)
                        storage.saveDiscrepancyReason(null)
                        logger("Clock discrepancy resolved safely: normal tracking resumed.")
                    }
                } else {
                    _isClockDiscrepancyDetected.value = true
                    _discrepancyReason.value = discrepancyMsg
                    storage.saveClockDiscrepancyDetected(true)
                    storage.saveDiscrepancyReason(discrepancyMsg)
                }
            } else if (discrepancyFound) {
                _isClockDiscrepancyDetected.value = true
                _discrepancyReason.value = discrepancyMsg
                storage.saveClockDiscrepancyDetected(true)
                storage.saveDiscrepancyReason(discrepancyMsg)
                logger("Suspicious clock change detected: $discrepancyMsg")
                historyRepository?.recordClockDiscrepancy(_storedRecordDate)
            }

            // 1. Calendar Date Rollover Evaluation
            val currentDate = dateProvider()
            if (currentDate != _storedRecordDate) {
                // Reject rollover if active discrepancy detected or wall clock moved backward
                val canRollover = if (_isClockDiscrepancyDetected.value) {
                    logger("Daily reset rejected: active clock discrepancy detected.")
                    false
                } else if (_lastWallClockMs > 0L && nowWall < _lastWallClockMs) {
                    logger("Daily reset rejected: current wall time is before last recorded time.")
                    false
                } else {
                    true
                }

                if (canRollover) {
                    logger("Daily reset triggered: date rolled over from '$_storedRecordDate' to '$currentDate'")
                    _storedRecordDate = currentDate
                    _baseDailyAccumulatedMs = 0L
                    _extraAllowanceMinutes.value = 0L
                    _extraAllowanceHistory.clear()
                    _extraHistoryFlow.value = emptyList()
                    _isRestrictionRelaxed.value = false
                    _specialDayReason.value = null
                    _dayStartWallClockMs = nowWall
                    _lastTimeZone = currentTimeZone
                    storage.saveStoredDate(currentDate)
                    storage.saveDailyAccumulatedMs(0L)
                    storage.saveExtraAllowanceMinutes(0L)
                    storage.saveExtraAllowanceHistory(emptyList())
                    storage.saveRestrictionRelaxed(false)
                    storage.saveSpecialDayReason(null)
                    storage.saveDayStartWallClockMs(nowWall)
                    storage.saveLastTimeZone(currentTimeZone)
                    if (isTracking) {
                        lastEligibleObservationTimeMs = nowMono
                    }
                } else {
                    logger("Preserving usage for '$_storedRecordDate' due to clock integrity guard.")
                }
            }

            // Update timing anchors
            if (!_isClockDiscrepancyDetected.value || nowWall >= _lastWallClockMs) {
                _lastWallClockMs = nowWall
                storage.saveLastWallClockMs(nowWall)
            }
            _lastElapsedRealtimeMs = nowMono
            storage.saveLastElapsedRealtimeMs(nowMono)
            if (currentBootCount != -1) {
                _lastBootCount = currentBootCount
                storage.saveLastBootCount(currentBootCount)
            }

            // 2. Settle stale observation timeout
            if (isTracking && lastEligibleObservationTimeMs != null) {
                val elapsed = nowMono - lastEligibleObservationTimeMs!!
                if (elapsed >= staleObservationTimeoutMs) {
                    val limit = effectiveLimitMs
                    val isRestricted = _isAllowanceEnabled.value && !_isRestrictionRelaxed.value && limit > 0L
                    if (isRestricted && _baseDailyAccumulatedMs >= limit) {
                        // Already exhausted, do not add stale timeout time and do not reduce usage
                    } else if (isRestricted && _baseDailyAccumulatedMs + staleObservationTimeoutMs >= limit) {
                        _baseDailyAccumulatedMs = maxOf(_baseDailyAccumulatedMs, limit)
                        storage.saveDailyAccumulatedMs(_baseDailyAccumulatedMs)
                    } else {
                        _baseDailyAccumulatedMs += staleObservationTimeoutMs
                        storage.saveDailyAccumulatedMs(_baseDailyAccumulatedMs)
                    }
                    isTracking = false
                    lastEligibleObservationTimeMs = null
                    logger("Stale observation timeout expired: session paused, accumulated now ${_baseDailyAccumulatedMs}ms")
                }
            }

            updateExposedFlows(nowMono)

            // Update daily record in history repository
            historyRepository?.recordDailyUsage(
                date = _storedRecordDate,
                accumulatedMs = currentTotalAccumulatedMs(nowMono),
                normalLimitMinutes = _dailyLimitMinutes.value,
                extraAllowanceMinutes = _extraAllowanceMinutes.value,
                isAllowanceEnabled = _isAllowanceEnabled.value,
                isRestrictionRelaxed = _isRestrictionRelaxed.value,
                allowanceExhausted = isExhaustedInternal(nowMono)
            )
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
            checkAndNotifyRestrictionDismissal()
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
            checkAndNotifyRestrictionDismissal()
        }
    }

    /**
     * Grants extra allowance minutes for today.
     * Prevents duplicate grants from rapid repeated taps within [DUPLICATE_ACTION_DEBOUNCE_MS].
     * Reasons are purely optional and never affect eligibility or calculation.
     *
     * @return True if extra allowance was successfully granted, false if rejected as duplicate or invalid.
     */
    fun addExtraAllowance(minutes: Long, reason: String? = null): Boolean {
        if (minutes <= 0L) return false
        val now = clock()
        synchronized(lock) {
            syncState(now)
            if (lastGrantTimeMs > 0L && (now - lastGrantTimeMs) < DUPLICATE_ACTION_DEBOUNCE_MS) {
                logger("Duplicate extra allowance request within ${DUPLICATE_ACTION_DEBOUNCE_MS}ms ignored.")
                return false
            }
            lastGrantTimeMs = now
            val updated = _extraAllowanceMinutes.value + minutes
            _extraAllowanceMinutes.value = updated
            _extraAllowanceHistory.add(minutes)
            storage.saveExtraAllowanceMinutes(updated)
            storage.saveExtraAllowanceHistory(_extraAllowanceHistory)
            _extraHistoryFlow.value = _extraAllowanceHistory.toList()

            if (!reason.isNullOrBlank()) {
                _specialDayReason.value = reason
                storage.saveSpecialDayReason(reason)
            }

            updateExposedFlows(now)
            checkAndNotifyRestrictionDismissal()
            logger("Granted +${minutes}m extra allowance today. Total extra: ${updated}m.")
            return true
        }
    }

    /**
     * Undoes the most recent extra-time addition.
     * Restores effective allowance immediately and triggers restriction if usage >= new limit.
     * Preserves accumulated usage, normal daily limit, Relax Today state, and clock-integrity state.
     *
     * @return True if an addition was undone, false if no extra allowance is active.
     */
    fun undoLastExtraAllowance(): Boolean {
        val now = clock()
        synchronized(lock) {
            syncState(now)
            if (_extraAllowanceMinutes.value <= 0L) {
                logger("Undo extra allowance rejected: no active extra allowance.")
                return false
            }
            val lastAdded = if (_extraAllowanceHistory.isNotEmpty()) {
                _extraAllowanceHistory.removeAt(_extraAllowanceHistory.size - 1)
            } else {
                _extraAllowanceMinutes.value
            }
            val updated = maxOf(0L, _extraAllowanceMinutes.value - lastAdded)
            _extraAllowanceMinutes.value = updated
            storage.saveExtraAllowanceMinutes(updated)
            storage.saveExtraAllowanceHistory(_extraAllowanceHistory)
            _extraHistoryFlow.value = _extraAllowanceHistory.toList()

            if (updated == 0L) {
                _specialDayReason.value = null
                storage.saveSpecialDayReason(null)
            }

            updateExposedFlows(now)
            checkAndNotifyRestrictionDismissal()
            logger("Undid last extra allowance (-${lastAdded}m). Total extra is now: ${updated}m.")
            return true
        }
    }

    /**
     * Clears all currently active extra allowance for today after confirmation.
     * Restores effective allowance immediately to the normal limit and triggers restriction if usage >= normal limit.
     * Preserves accumulated usage, normal daily limit, Relax Today state, and clock-integrity state.
     *
     * @return True if extra allowance was removed, false if no extra allowance was active.
     */
    fun removeAllExtraAllowance(): Boolean {
        val now = clock()
        synchronized(lock) {
            syncState(now)
            if (_extraAllowanceMinutes.value <= 0L) {
                logger("Remove all extra rejected: no active extra allowance.")
                return false
            }
            val removed = _extraAllowanceMinutes.value
            _extraAllowanceMinutes.value = 0L
            _extraAllowanceHistory.clear()
            storage.saveExtraAllowanceMinutes(0L)
            storage.saveExtraAllowanceHistory(emptyList())
            _extraHistoryFlow.value = emptyList()
            _specialDayReason.value = null
            storage.saveSpecialDayReason(null)

            updateExposedFlows(now)
            checkAndNotifyRestrictionDismissal()
            logger("Removed all extra allowance (-${removed}m). Extra allowance is now: 0m.")
            return true
        }
    }

    /**
     * Temporarily relaxes Reels restriction for the rest of today.
     * Preserves existing usage history. Normal restrictions return automatically tomorrow.
     * Reasons are purely optional and never affect eligibility.
     */
    fun relaxRestrictionForToday(reason: String? = null): Boolean {
        val now = clock()
        synchronized(lock) {
            syncState(now)
            _isRestrictionRelaxed.value = true
            storage.saveRestrictionRelaxed(true)

            if (!reason.isNullOrBlank()) {
                _specialDayReason.value = reason
                storage.saveSpecialDayReason(reason)
            }

            updateExposedFlows(now)
            checkAndNotifyRestrictionDismissal()
            logger("Temporarily relaxed restrictions for today. Reason: ${reason ?: "None specified"}.")
            return true
        }
    }

    /**
     * Resumes normal restrictions for today if previously relaxed.
     */
    fun resumeNormalRestrictions(): Boolean {
        val now = clock()
        synchronized(lock) {
            syncState(now)
            _isRestrictionRelaxed.value = false
            storage.saveRestrictionRelaxed(false)
            updateExposedFlows(now)
            logger("Resumed normal restrictions for today.")
            return true
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
            checkAndNotifyRestrictionDismissal()
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
                        val added = minOf(elapsed, staleObservationTimeoutMs)
                        val limit = effectiveLimitMs
                        val isRestricted = _isAllowanceEnabled.value && !_isRestrictionRelaxed.value && limit > 0L
                        if (isRestricted && _baseDailyAccumulatedMs + added >= limit) {
                            _baseDailyAccumulatedMs = maxOf(_baseDailyAccumulatedMs, limit)
                        } else {
                            _baseDailyAccumulatedMs += added
                        }
                        storage.saveDailyAccumulatedMs(_baseDailyAccumulatedMs)
                    }
                }
                isTracking = false
                lastEligibleObservationTimeMs = null
                updateExposedFlows(now)
                return false
            }

            // Eligible Reels observation
            val limit = effectiveLimitMs
            val isRestricted = _isAllowanceEnabled.value && !_isRestrictionRelaxed.value && limit > 0L

            if (isRestricted && _baseDailyAccumulatedMs >= limit) {
                // Effective allowance is already exhausted.
                // Do not add any usage while exhausted, and never erase previously accumulated usage.
                isTracking = true
                lastEligibleObservationTimeMs = now
                updateExposedFlows(now)
                return true
            }

            if (isTracking && lastEligibleObservationTimeMs != null) {
                val elapsed = now - lastEligibleObservationTimeMs!!
                if (elapsed > 0) {
                    val added = minOf(elapsed, staleObservationTimeoutMs)
                    if (isRestricted && _baseDailyAccumulatedMs + added >= limit) {
                        _baseDailyAccumulatedMs = maxOf(_baseDailyAccumulatedMs, limit)
                        storage.saveDailyAccumulatedMs(_baseDailyAccumulatedMs)
                        isTracking = true
                        lastEligibleObservationTimeMs = now
                        updateExposedFlows(now)
                        return true
                    } else {
                        _baseDailyAccumulatedMs += added
                        storage.saveDailyAccumulatedMs(_baseDailyAccumulatedMs)
                    }
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

        val effectiveLimit = if (_isAllowanceEnabled.value) {
            _dailyLimitMinutes.value + _extraAllowanceMinutes.value
        } else {
            0L
        }
        _effectiveLimitMinutes.value = effectiveLimit
        val effectiveLimitInMs = effectiveLimit * 60 * 1000L

        _allowanceState.value = when {
            !_isAllowanceEnabled.value || (_dailyLimitMinutes.value == 0L && _extraAllowanceMinutes.value == 0L) -> AllowanceState.DISABLED
            _isRestrictionRelaxed.value -> AllowanceState.RELAXED
            effectiveLimitInMs > 0L && currentTotal >= effectiveLimitInMs -> AllowanceState.EXHAUSTED
            isTracking -> AllowanceState.TRACKING_WITHIN_ALLOWANCE
            else -> AllowanceState.PAUSED_WITHIN_ALLOWANCE
        }
    }

    private fun checkAndNotifyRestrictionDismissal() {
        if (!isExhaustedInternal()) {
            onRestrictionDismissRequested?.invoke()
        }
    }
}

