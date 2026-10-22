package com.outloop.prototype.repository

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

/**
 * Unit tests for [UserPreferencesRepository] verifying onboarding completion state persistence.
 */
class UserPreferencesRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockPrefs = mock(SharedPreferences::class.java)
        mockEditor = mock(SharedPreferences.Editor::class.java)

        `when`(mockContext.getSharedPreferences(eq("outloop_user_prefs"), eq(Context.MODE_PRIVATE)))
            .thenReturn(mockPrefs)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(any(), any())).thenReturn(mockEditor)
        `when`(mockEditor.putInt(any(), any())).thenReturn(mockEditor)
        `when`(mockPrefs.getBoolean(eq("key_continuous_break_enabled"), eq(true))).thenReturn(true)
        `when`(mockPrefs.getInt(eq("key_continuous_break_interval_mins"), eq(5))).thenReturn(5)
    }

    @Test
    fun testInitialOnboardingStateIsFalseWhenNotSet() {
        `when`(mockPrefs.getBoolean(eq("key_onboarding_completed"), eq(false))).thenReturn(false)

        UserPreferencesRepository.init(mockContext)

        assertFalse(UserPreferencesRepository.isOnboardingCompleted.value)
    }

    @Test
    fun testInitialOnboardingStateIsTrueWhenPersistedTrue() {
        `when`(mockPrefs.getBoolean(eq("key_onboarding_completed"), eq(false))).thenReturn(true)

        UserPreferencesRepository.init(mockContext)

        assertTrue(UserPreferencesRepository.isOnboardingCompleted.value)
    }

    @Test
    fun testSetOnboardingCompletedUpdatesStateAndPersists() {
        UserPreferencesRepository.setOnboardingCompleted(mockContext, true)

        assertTrue(UserPreferencesRepository.isOnboardingCompleted.value)
        verify(mockEditor).putBoolean("key_onboarding_completed", true)
        verify(mockEditor).apply()
    }

    @Test
    fun testResetOnboardingSetsStateToFalse() {
        UserPreferencesRepository.setOnboardingCompleted(mockContext, true)
        assertTrue(UserPreferencesRepository.isOnboardingCompleted.value)

        UserPreferencesRepository.resetOnboarding(mockContext)
        assertFalse(UserPreferencesRepository.isOnboardingCompleted.value)
        verify(mockEditor).putBoolean("key_onboarding_completed", false)
    }

    @Test
    fun testContinuousBreakDefaultSettings() {
        UserPreferencesRepository.init(mockContext)

        assertTrue("Continuous break default enabled must be true", UserPreferencesRepository.isContinuousBreakEnabled.value)
        org.junit.Assert.assertEquals("Continuous break default interval must be 5m", 5, UserPreferencesRepository.continuousBreakIntervalMinutes.value)
    }

    @Test
    fun testSetContinuousBreakEnabledPersists() {
        UserPreferencesRepository.setContinuousBreakEnabled(mockContext, false)

        assertFalse(UserPreferencesRepository.isContinuousBreakEnabled.value)
        verify(mockEditor).putBoolean("key_continuous_break_enabled", false)
        verify(mockEditor).apply()
    }

    @Test
    fun testSetContinuousBreakIntervalMinutesPersists() {
        UserPreferencesRepository.setContinuousBreakIntervalMinutes(mockContext, 10)

        org.junit.Assert.assertEquals(10, UserPreferencesRepository.continuousBreakIntervalMinutes.value)
        verify(mockEditor).putInt("key_continuous_break_interval_mins", 10)
        verify(mockEditor).apply()
    }
}
