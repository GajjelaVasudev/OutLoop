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

    private const val PREFS_NAME = "outloop_user_prefs"
    private const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"

    private val _isOnboardingCompleted = MutableStateFlow(false)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isOnboardingCompleted.value = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _isOnboardingCompleted.value = completed
    }

    fun resetOnboarding(context: Context) {
        setOnboardingCompleted(context, false)
    }
}
