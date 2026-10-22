package com.outloop.prototype.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages persistent user preferences, including first-launch onboarding status.
 * All preference data is stored strictly on-device via SharedPreferences.
 */
object UserPreferencesRepository {

    const val PREFS_NAME = "outloop_user_prefs"
    const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"
    const val KEY_CONTINUOUS_BREAK_ENABLED = "key_continuous_break_enabled"
    const val KEY_CONTINUOUS_BREAK_INTERVAL_MINS = "key_continuous_break_interval_mins"

    private val _isOnboardingCompleted = MutableStateFlow(false)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _isContinuousBreakEnabled = MutableStateFlow(true)
    val isContinuousBreakEnabled: StateFlow<Boolean> = _isContinuousBreakEnabled.asStateFlow()

    private val _continuousBreakIntervalMinutes = MutableStateFlow(5)
    val continuousBreakIntervalMinutes: StateFlow<Int> = _continuousBreakIntervalMinutes.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isOnboardingCompleted.value = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        _isContinuousBreakEnabled.value = prefs.getBoolean(KEY_CONTINUOUS_BREAK_ENABLED, true)
        _continuousBreakIntervalMinutes.value = prefs.getInt(KEY_CONTINUOUS_BREAK_INTERVAL_MINS, 5)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _isOnboardingCompleted.value = completed
    }

    fun resetOnboarding(context: Context) {
        setOnboardingCompleted(context, false)
    }

    fun setContinuousBreakEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CONTINUOUS_BREAK_ENABLED, enabled).apply()
        _isContinuousBreakEnabled.value = enabled
    }

    fun setContinuousBreakIntervalMinutes(context: Context, intervalMins: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CONTINUOUS_BREAK_INTERVAL_MINS, intervalMins).apply()
        _continuousBreakIntervalMinutes.value = intervalMins
    }

    internal fun notifyContinuousBreakEnabledChanged(enabled: Boolean) {
        _isContinuousBreakEnabled.value = enabled
    }

    internal fun notifyContinuousBreakIntervalChanged(intervalMins: Int) {
        _continuousBreakIntervalMinutes.value = intervalMins
    }
}
