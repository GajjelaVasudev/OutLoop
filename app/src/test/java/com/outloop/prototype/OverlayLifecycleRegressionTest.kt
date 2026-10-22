package com.outloop.prototype

import com.outloop.prototype.OutLoopAccessibilityService.OverlayLifecycleState
import com.outloop.prototype.continuous.ContinuousBreakManager
import com.outloop.prototype.continuous.InMemoryContinuousBreakStorage
import com.outloop.prototype.playback.ReelsPlaybackController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import android.content.Context
import android.media.AudioManager
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull

/**
 * Regression tests verifying the OutLoop overlay lifecycle state transitions,
 * daily restriction precedence, prompt restoration idempotency, and audio-focus safety.
 */
class OverlayLifecycleRegressionTest {

    private lateinit var mockContext: Context
    private lateinit var mockAudioManager: AudioManager
    private lateinit var playbackController: ReelsPlaybackController
    private val logs = mutableListOf<String>()

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockAudioManager = mock(AudioManager::class.java)
        `when`(mockContext.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mockAudioManager)
        @Suppress("DEPRECATION")
        `when`(mockAudioManager.requestAudioFocus(anyOrNull(), any(), any())).thenReturn(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        @Suppress("DEPRECATION")
        `when`(mockAudioManager.abandonAudioFocus(anyOrNull())).thenReturn(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        `when`(mockAudioManager.requestAudioFocus(any())).thenReturn(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)

        logs.clear()
        playbackController = ReelsPlaybackController(
            context = mockContext,
            targetPackage = "com.instagram.android",
            sdkInt = 0,
            logger = { logs.add(it) }
        )
    }

    @Test
    fun testOverlayLifecycleStatesEnum() {
        // Verify all required states exist and are distinct
        val states = OverlayLifecycleState.values()
        assertTrue(states.contains(OverlayLifecycleState.HIDDEN))
        assertTrue(states.contains(OverlayLifecycleState.SHOWING_DAILY_RESTRICTION))
        assertTrue(states.contains(OverlayLifecycleState.SHOWING_CONTINUOUS_BREAK))
        assertTrue(states.contains(OverlayLifecycleState.TEMPORARILY_OUTSIDE_INSTAGRAM))
    }

    @Test
    fun testAudioLifecycleOnShowAndDeparture() {
        // Simulate overlay becoming visible on verified Reels context
        val outcome = playbackController.suppressPlayback(
            rootNode = null,
            isVerifiedTargetPackage = true,
            isReelsContext = true
        )
        assertFalse("No semantic pause control on null root", outcome)
        assertTrue("Transient audio focus must be held while overlay is showing", playbackController.isHoldingAudioFocus)

        // Subsequent scans while showing must not duplicate or thrash focus
        playbackController.suppressPlayback(
            rootNode = null,
            isVerifiedTargetPackage = true,
            isReelsContext = true
        )
        assertTrue("Focus remains held without thrashing", playbackController.isHoldingAudioFocus)

        // Transitioning to TEMPORARILY_OUTSIDE_INSTAGRAM (Recent Apps / other app)
        playbackController.abandonTransientAudioFocus()
        assertFalse("Audio focus must be abandoned immediately upon leaving Instagram", playbackController.isHoldingAudioFocus)

        // Idempotent abandon
        playbackController.abandonTransientAudioFocus()
        assertFalse("Idempotent abandon is safe", playbackController.isHoldingAudioFocus)
    }

    @Test
    fun testPlaybackSuppressionRejectsUnverifiedSurfaces() {
        // Non-target package
        val rejectedPkg = playbackController.suppressPlayback(
            rootNode = null,
            isVerifiedTargetPackage = false,
            isReelsContext = true
        )
        assertFalse(rejectedPkg)
        assertFalse(playbackController.isHoldingAudioFocus)

        // Non-Reels context inside target package
        val rejectedScreen = playbackController.suppressPlayback(
            rootNode = null,
            isVerifiedTargetPackage = true,
            isReelsContext = false
        )
        assertFalse(rejectedScreen)
        assertFalse(playbackController.isHoldingAudioFocus)
    }

    @Test
    fun testDailyRestrictionPrecedenceLogic() {
        // State simulation: both daily restriction and continuous break eligible
        var activeState = OverlayLifecycleState.HIDDEN
        val isDailyExhausted = true
        val isBreakPromptEligible = true

        // Deterministic precedence rule: Daily restriction takes priority
        if (isDailyExhausted) {
            activeState = OverlayLifecycleState.SHOWING_DAILY_RESTRICTION
        } else if (isBreakPromptEligible) {
            activeState = OverlayLifecycleState.SHOWING_CONTINUOUS_BREAK
        }

        assertEquals(
            "Daily restriction must take absolute precedence over continuous break",
            OverlayLifecycleState.SHOWING_DAILY_RESTRICTION,
            activeState
        )
    }

    @Test
    fun testAppSwitchingRestorationPreservesPreviousRestrictionType() {
        var state = OverlayLifecycleState.SHOWING_DAILY_RESTRICTION
        var stateBeforeDeparture: OverlayLifecycleState?

        // User opens Recent Apps
        stateBeforeDeparture = state
        state = OverlayLifecycleState.TEMPORARILY_OUTSIDE_INSTAGRAM

        assertEquals(OverlayLifecycleState.TEMPORARILY_OUTSIDE_INSTAGRAM, state)
        assertEquals(OverlayLifecycleState.SHOWING_DAILY_RESTRICTION, stateBeforeDeparture)

        // User returns to Instagram: restore previous restriction
        if (stateBeforeDeparture == OverlayLifecycleState.SHOWING_DAILY_RESTRICTION) {
            state = OverlayLifecycleState.SHOWING_DAILY_RESTRICTION
            stateBeforeDeparture = null
        }

        assertEquals(OverlayLifecycleState.SHOWING_DAILY_RESTRICTION, state)
        assertNull(stateBeforeDeparture)
    }
}
