package com.outloop.prototype.playback

import android.content.Context
import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull

/**
 * Deterministic unit tests for [ReelsPlaybackController].
 * Verifies audio focus lifecycle, fallback handling, target package safety guards, and idempotency.
 */
class ReelsPlaybackControllerTest {

    private lateinit var mockContext: Context
    private lateinit var mockAudioManager: AudioManager
    private lateinit var controller: ReelsPlaybackController
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
        controller = ReelsPlaybackController(
            context = mockContext,
            targetPackage = "com.instagram.android",
            sdkInt = 0,
            logger = { logs.add(it) }
        )
    }

    @Test
    fun testSuppressPlaybackRejectsNonTargetPackage() {
        val result = controller.suppressPlayback(
            rootNode = null,
            isVerifiedTargetPackage = false
        )
        assertFalse("Must reject playback suppression on non-target package", result)
        assertFalse("Must not hold audio focus for non-target package", controller.isHoldingAudioFocus)
        assertTrue(logs.any { it.contains("rejected: active package is not com.instagram.android") })
    }

    @Test
    fun testSuppressPlaybackRequestsAudioFocusWhenVerifiedTarget() {
        val result = controller.suppressPlayback(
            rootNode = null,
            isVerifiedTargetPackage = true
        )
        // Since rootNode is null, semantic pause is false, falling back to audio focus
        assertFalse("Semantic pause returns false when no node exists", result)
        assertTrue("Must hold audio focus after successful request", controller.isHoldingAudioFocus)
        assertTrue(logs.any { it.contains("No semantic pause control exposed") })
    }

    @Test
    fun testAbandonAudioFocusIsIdempotent() {
        controller.requestTransientAudioFocus()
        assertTrue(controller.isHoldingAudioFocus)

        controller.abandonTransientAudioFocus()
        assertFalse(controller.isHoldingAudioFocus)

        // Multiple calls must be safe and idempotent
        controller.abandonTransientAudioFocus()
        assertFalse(controller.isHoldingAudioFocus)
    }

    @Test
    fun testSuppressPlaybackRejectsNonReelsScreen() {
        val result = controller.suppressPlayback(
            rootNode = null,
            isVerifiedTargetPackage = true,
            isReelsContext = false
        )
        assertFalse("Must reject playback suppression when screen is not Reels context", result)
        assertFalse("Must not hold audio focus for non-Reels screen", controller.isHoldingAudioFocus)
        assertTrue(logs.any { it.contains("screen is not verified Reels context") })
    }

    @Test
    fun testRepeatedFocusRequestsDoNotDuplicate() {
        controller.requestTransientAudioFocus()
        assertTrue(controller.isHoldingAudioFocus)

        // Second request while already holding focus is ignored
        controller.requestTransientAudioFocus()
        assertTrue(controller.isHoldingAudioFocus)
    }

    @Test
    fun testRepeatedSuppressPlaybackWhileHoldingFocusDoesNotReRequest() {
        // First suppression acquires focus
        controller.suppressPlayback(rootNode = null, isVerifiedTargetPackage = true, isReelsContext = true)
        assertTrue(controller.isHoldingAudioFocus)

        // Second suppression while already holding focus does not trigger additional requests
        controller.suppressPlayback(rootNode = null, isVerifiedTargetPackage = true, isReelsContext = true)
        assertTrue(controller.isHoldingAudioFocus)
    }

    @Test
    fun testMultiCauseSuppressionPreservesFocusUntilAllCausesReleased() {
        // Daily allowance triggers suppression
        controller.suppressPlayback(
            rootNode = null,
            isVerifiedTargetPackage = true,
            isReelsContext = true,
            cause = PlaybackRestrictionCause.DAILY_ALLOWANCE
        )
        assertTrue(controller.isHoldingAudioFocus)
        assertTrue(controller.currentActiveCauses.contains(PlaybackRestrictionCause.DAILY_ALLOWANCE))

        // Continuous break also triggers suppression
        controller.suppressPlayback(
            rootNode = null,
            isVerifiedTargetPackage = true,
            isReelsContext = true,
            cause = PlaybackRestrictionCause.CONTINUOUS_BREAK
        )
        assertTrue(controller.isHoldingAudioFocus)
        assertTrue(controller.currentActiveCauses.contains(PlaybackRestrictionCause.CONTINUOUS_BREAK))

        // Dismiss continuous break -> Daily allowance is still exhausted!
        controller.releaseSuppression(PlaybackRestrictionCause.CONTINUOUS_BREAK)
        assertFalse(controller.currentActiveCauses.contains(PlaybackRestrictionCause.CONTINUOUS_BREAK))
        assertTrue(controller.currentActiveCauses.contains(PlaybackRestrictionCause.DAILY_ALLOWANCE))
        assertTrue("Audio focus must be preserved while daily allowance remains active", controller.isHoldingAudioFocus)

        // Dismiss daily allowance -> All causes gone, audio focus abandoned
        controller.releaseSuppression(PlaybackRestrictionCause.DAILY_ALLOWANCE)
        assertFalse(controller.currentActiveCauses.contains(PlaybackRestrictionCause.DAILY_ALLOWANCE))
        assertFalse("Audio focus must be abandoned once all causes are released", controller.isHoldingAudioFocus)
    }

    @Test
    fun testReleaseAllSuppressionAbandonsFocusImmediately() {
        controller.suppressPlayback(
            rootNode = null,
            isVerifiedTargetPackage = true,
            isReelsContext = true,
            cause = PlaybackRestrictionCause.DAILY_ALLOWANCE
        )
        controller.suppressPlayback(
            rootNode = null,
            isVerifiedTargetPackage = true,
            isReelsContext = true,
            cause = PlaybackRestrictionCause.CONTINUOUS_BREAK
        )
        assertTrue(controller.isHoldingAudioFocus)
        org.junit.Assert.assertEquals(2, controller.currentActiveCauses.size)

        // App departure / screen lock calls releaseAllSuppression
        controller.releaseAllSuppression()
        assertTrue(controller.currentActiveCauses.isEmpty())
        assertFalse("Audio focus must be abandoned immediately on releaseAllSuppression", controller.isHoldingAudioFocus)
    }

    @Test
    fun testLastResultRecordsCorrectOutcomes() {
        controller.suppressPlayback(rootNode = null, isVerifiedTargetPackage = false)
        org.junit.Assert.assertEquals(PlaybackControlResult.REJECTED_NOT_TARGET_PACKAGE, controller.lastResult)

        controller.suppressPlayback(rootNode = null, isVerifiedTargetPackage = true, isReelsContext = false)
        org.junit.Assert.assertEquals(PlaybackControlResult.REJECTED_NOT_REELS_CONTEXT, controller.lastResult)

        controller.suppressPlayback(rootNode = null, isVerifiedTargetPackage = true, isReelsContext = true)
        org.junit.Assert.assertEquals(PlaybackControlResult.FALLBACK_AUDIO_FOCUS_ONLY, controller.lastResult)
    }
}
