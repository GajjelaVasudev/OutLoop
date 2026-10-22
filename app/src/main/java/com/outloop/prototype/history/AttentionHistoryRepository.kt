package com.outloop.prototype.history

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Storage abstraction for persisting daily attention history records.
 * Enables deterministic JVM unit testing without Android SharedPreferences.
 */
interface AttentionHistoryStorage {
    fun loadRecords(): List<DailyAttentionRecord>
    fun saveRecords(records: List<DailyAttentionRecord>)
    fun clear()
}

/**
 * SharedPreferences storage implementation serializing aggregated daily records as JSON.
 */
class SharedPrefsAttentionHistoryStorage(context: Context) : AttentionHistoryStorage {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "outloop_attention_history_prefs"
        private const val KEY_RECORDS_JSON = "key_records_json"
    }

    override fun loadRecords(): List<DailyAttentionRecord> {
        val jsonString = prefs.getString(KEY_RECORDS_JSON, null) ?: return emptyList()
        val list = mutableListOf<DailyAttentionRecord>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    DailyAttentionRecord(
                        date = obj.optString("date", ""),
                        accumulatedReelsMs = obj.optLong("accumulatedReelsMs", 0L),
                        normalLimitMinutes = obj.optLong("normalLimitMinutes", 0L),
                        extraAllowanceMinutes = obj.optLong("extraAllowanceMinutes", 0L),
                        effectiveLimitMinutes = obj.optLong("effectiveLimitMinutes", 0L),
                        isAllowanceEnabled = obj.optBoolean("isAllowanceEnabled", false),
                        isRestrictionRelaxed = obj.optBoolean("isRestrictionRelaxed", false),
                        allowanceExhausted = obj.optBoolean("allowanceExhausted", false),
                        restrictionDisplayedCount = obj.optInt("restrictionDisplayedCount", 0),
                        restrictionDismissedCount = obj.optInt("restrictionDismissedCount", 0),
                        scrollRescueResetsCompleted = obj.optInt("scrollRescueResetsCompleted", 0),
                        hasSufficientTrackingCoverage = obj.optBoolean("hasSufficientTrackingCoverage", true),
                        clockDiscrepancyObserved = obj.optBoolean("clockDiscrepancyObserved", false)
                    )
                )
            }
        } catch (_: Exception) {
            // Safe fallback on parse error
        }
        return list
    }

    override fun saveRecords(records: List<DailyAttentionRecord>) {
        val jsonArray = JSONArray()
        for (rec in records) {
            val obj = JSONObject().apply {
                put("date", rec.date)
                put("accumulatedReelsMs", rec.accumulatedReelsMs)
                put("normalLimitMinutes", rec.normalLimitMinutes)
                put("extraAllowanceMinutes", rec.extraAllowanceMinutes)
                put("effectiveLimitMinutes", rec.effectiveLimitMinutes)
                put("isAllowanceEnabled", rec.isAllowanceEnabled)
                put("isRestrictionRelaxed", rec.isRestrictionRelaxed)
                put("allowanceExhausted", rec.allowanceExhausted)
                put("restrictionDisplayedCount", rec.restrictionDisplayedCount)
                put("restrictionDismissedCount", rec.restrictionDismissedCount)
                put("scrollRescueResetsCompleted", rec.scrollRescueResetsCompleted)
                put("hasSufficientTrackingCoverage", rec.hasSufficientTrackingCoverage)
                put("clockDiscrepancyObserved", rec.clockDiscrepancyObserved)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_RECORDS_JSON, jsonArray.toString()).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

/**
 * In-memory storage implementation for JVM unit tests.
 */
class InMemoryAttentionHistoryStorage : AttentionHistoryStorage {
    private var inMemoryRecords: List<DailyAttentionRecord> = emptyList()

    override fun loadRecords(): List<DailyAttentionRecord> = inMemoryRecords
    override fun saveRecords(records: List<DailyAttentionRecord>) {
        inMemoryRecords = records.toList()
    }
    override fun clear() {
        inMemoryRecords = emptyList()
    }
}

/**
 * Repository managing persistent aggregated daily attention history.
 */
class AttentionHistoryRepository(
    private val storage: AttentionHistoryStorage = InMemoryAttentionHistoryStorage()
) {
    companion object {
        const val MAX_HISTORY_DAYS = 30

        @Volatile
        private var instance: AttentionHistoryRepository? = null

        fun getInstance(context: Context): AttentionHistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: AttentionHistoryRepository(
                    storage = SharedPrefsAttentionHistoryStorage(context.applicationContext)
                ).also { instance = it }
            }
        }
    }

    private val lock = Any()
    private val _recordsMap = mutableMapOf<String, DailyAttentionRecord>()

    private val _allRecords = MutableStateFlow<List<DailyAttentionRecord>>(emptyList())
    val allRecords: StateFlow<List<DailyAttentionRecord>> = _allRecords.asStateFlow()

    private val _todayRecord = MutableStateFlow<DailyAttentionRecord?>(null)
    val todayRecord: StateFlow<DailyAttentionRecord?> = _todayRecord.asStateFlow()

    init {
        synchronized(lock) {
            val loaded = storage.loadRecords()
            for (rec in loaded) {
                if (rec.date.isNotBlank()) {
                    _recordsMap[rec.date] = rec
                }
            }
            updateExposedFlows()
        }
    }

    fun getRecord(date: String): DailyAttentionRecord {
        return synchronized(lock) {
            _recordsMap[date] ?: DailyAttentionRecord(date = date)
        }
    }

    fun recordDailyUsage(
        date: String,
        accumulatedMs: Long,
        normalLimitMinutes: Long,
        extraAllowanceMinutes: Long,
        isAllowanceEnabled: Boolean,
        isRestrictionRelaxed: Boolean,
        allowanceExhausted: Boolean
    ) {
        if (date.isBlank()) return
        synchronized(lock) {
            val existing = _recordsMap[date] ?: DailyAttentionRecord(date = date)
            val updated = existing.copy(
                accumulatedReelsMs = accumulatedMs,
                normalLimitMinutes = normalLimitMinutes,
                extraAllowanceMinutes = extraAllowanceMinutes,
                effectiveLimitMinutes = if (isAllowanceEnabled) normalLimitMinutes + extraAllowanceMinutes else 0L,
                isAllowanceEnabled = isAllowanceEnabled,
                isRestrictionRelaxed = isRestrictionRelaxed,
                allowanceExhausted = allowanceExhausted
            )
            _recordsMap[date] = updated
            saveAndPublish()
        }
    }

    fun recordRestrictionDisplayed(date: String) {
        if (date.isBlank()) return
        synchronized(lock) {
            val existing = _recordsMap[date] ?: DailyAttentionRecord(date = date)
            _recordsMap[date] = existing.copy(
                restrictionDisplayedCount = existing.restrictionDisplayedCount + 1,
                allowanceExhausted = true
            )
            saveAndPublish()
        }
    }

    fun recordRestrictionDismissed(date: String) {
        if (date.isBlank()) return
        synchronized(lock) {
            val existing = _recordsMap[date] ?: DailyAttentionRecord(date = date)
            _recordsMap[date] = existing.copy(
                restrictionDismissedCount = existing.restrictionDismissedCount + 1
            )
            saveAndPublish()
        }
    }

    fun recordScrollRescueResetCompleted(date: String) {
        if (date.isBlank()) return
        synchronized(lock) {
            val existing = _recordsMap[date] ?: DailyAttentionRecord(date = date)
            _recordsMap[date] = existing.copy(
                scrollRescueResetsCompleted = existing.scrollRescueResetsCompleted + 1
            )
            saveAndPublish()
        }
    }

    fun recordTrackingInterruption(date: String) {
        if (date.isBlank()) return
        synchronized(lock) {
            val existing = _recordsMap[date] ?: DailyAttentionRecord(date = date)
            _recordsMap[date] = existing.copy(
                hasSufficientTrackingCoverage = false
            )
            saveAndPublish()
        }
    }

    fun recordClockDiscrepancy(date: String) {
        if (date.isBlank()) return
        synchronized(lock) {
            val existing = _recordsMap[date] ?: DailyAttentionRecord(date = date)
            _recordsMap[date] = existing.copy(
                clockDiscrepancyObserved = true
            )
            saveAndPublish()
        }
    }

    fun setTodayDate(date: String) {
        synchronized(lock) {
            _todayRecord.value = _recordsMap[date] ?: DailyAttentionRecord(date = date)
        }
    }

    private fun saveAndPublish() {
        // Enforce max 30 days retention
        val sortedList = _recordsMap.values.sortedByDescending { it.date }.take(MAX_HISTORY_DAYS)
        _recordsMap.clear()
        for (rec in sortedList) {
            _recordsMap[rec.date] = rec
        }
        storage.saveRecords(sortedList)
        updateExposedFlows()
    }

    private fun updateExposedFlows() {
        val sortedList = _recordsMap.values.sortedByDescending { it.date }
        _allRecords.value = sortedList
        val latest = sortedList.firstOrNull()
        if (latest != null) {
            _todayRecord.value = latest
        }
    }
}
