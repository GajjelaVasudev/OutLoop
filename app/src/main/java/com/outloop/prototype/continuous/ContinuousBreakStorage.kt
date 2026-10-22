package com.outloop.prototype.continuous

import android.content.Context
import com.outloop.prototype.repository.UserPreferencesRepository

/**
 * Storage interface for Continuous Reels Break preferences.
 * Separated to enable fully deterministic, JVM-only unit testing without Android Context dependencies.
 */
interface ContinuousBreakStorage {
    fun loadBreakReminderEnabled(): Boolean
    fun saveBreakReminderEnabled(enabled: Boolean)
    fun loadBreakIntervalMinutes(): Int
    fun saveBreakIntervalMinutes(intervalMinutes: Int)
}

/**
 * Production SharedPreferences implementation backed by "outloop_user_prefs".
 */
class SharedPrefsContinuousBreakStorage(private val context: Context) : ContinuousBreakStorage {

    private val prefs by lazy {
        context.getSharedPreferences("outloop_user_prefs", Context.MODE_PRIVATE)
    }

    override fun loadBreakReminderEnabled(): Boolean {
        return prefs.getBoolean(UserPreferencesRepository.KEY_CONTINUOUS_BREAK_ENABLED, true)
    }

    override fun saveBreakReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(UserPreferencesRepository.KEY_CONTINUOUS_BREAK_ENABLED, enabled).apply()
        UserPreferencesRepository.notifyContinuousBreakEnabledChanged(enabled)
    }

    override fun loadBreakIntervalMinutes(): Int {
        return prefs.getInt(UserPreferencesRepository.KEY_CONTINUOUS_BREAK_INTERVAL_MINS, 5)
    }

    override fun saveBreakIntervalMinutes(intervalMinutes: Int) {
        prefs.edit().putInt(UserPreferencesRepository.KEY_CONTINUOUS_BREAK_INTERVAL_MINS, intervalMinutes).apply()
        UserPreferencesRepository.notifyContinuousBreakIntervalChanged(intervalMinutes)
    }
}

/**
 * In-memory storage for deterministic JVM testing.
 */
class InMemoryContinuousBreakStorage(
    private var enabled: Boolean = true,
    private var intervalMinutes: Int = 5
) : ContinuousBreakStorage {

    override fun loadBreakReminderEnabled(): Boolean = enabled

    override fun saveBreakReminderEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun loadBreakIntervalMinutes(): Int = intervalMinutes

    override fun saveBreakIntervalMinutes(intervalMinutes: Int) {
        this.intervalMinutes = intervalMinutes
    }
}
