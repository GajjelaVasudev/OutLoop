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
}
