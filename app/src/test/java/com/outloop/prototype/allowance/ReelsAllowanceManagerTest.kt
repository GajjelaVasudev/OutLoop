package com.outloop.prototype.allowance

import com.outloop.classifier.CandidateScreenState
import com.outloop.classifier.ClassificationResult
import com.outloop.classifier.Confidence
import com.outloop.classifier.DiagnosticSummary
import com.outloop.classifier.ObservationOutcome
import com.outloop.classifier.ReasonCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic JVM unit tests for [ReelsAllowanceManager].
 *
 * Verifies:
 * - True opt-in defaults (allowance disabled, 0 limit).
 * - Configurable daily limit and Off states.
 * - Bounded observation delta reuse (2s, 10s, 20s, 30s capping at 10s).
 * - Trailing interval accounting on transition away from Reels.
 * - Stale observation timeouts during silent intervals.
 * - Exact threshold exhaustion and state progression.
 * - Daily calendar date rollover and reset policy.
 * - In-memory persistent state restore across simulated restarts.
 * - Strict non-Reels screening (Stories, DMs, Feed, Profile never accumulate).
 */
class ReelsAllowanceManagerTest {

    private var currentTimeMs = 1_000_000L
    private var currentDate = "2026-10-21"
    private lateinit var storage: InMemoryAllowanceStorage
    private lateinit var manager: ReelsAllowanceManager

    @Before
    fun setUp() {
        currentTimeMs = 1_000_000L
        currentDate = "2026-10-21"
        storage = InMemoryAllowanceStorage()
        manager = ReelsAllowanceManager(
            clock = { currentTimeMs },
            dateProvider = { currentDate },
            storage = storage,
            staleObservationTimeoutMs = 10_000L
        )
    }

    private fun createResult(
        candidateState: CandidateScreenState = CandidateScreenState.REELS_TAB,
        confidence: Confidence = Confidence.HIGH,
        reasonCode: ReasonCode = ReasonCode.MULTIPLE_SIGNALS_MATCH,
        outcome: ObservationOutcome = ObservationOutcome.CLASSIFIED,
        isInstagramPackage: Boolean = true,
        isPartialTree: Boolean = false,
        reachedNodeLimit: Boolean = false
    ): ClassificationResult {
        return ClassificationResult(
            candidateState = candidateState,
            confidence = confidence,
            reasonCode = reasonCode,
            timestamp = currentTimeMs,
            evidenceExplanation = "Test observation",
            isDeviceVerified = true,
            diagnosticSummary = DiagnosticSummary(
                isInstagramPackage = isInstagramPackage,
                hasRootNode = true,
                observationOutcome = outcome,
                isPartialTree = isPartialTree,
                reachedNodeLimit = reachedNodeLimit,
                selectedTabId = "clips_tab"
            )
        )
    }

    @Test
    fun testInitialDefaultsAreOff() {
        assertFalse("Allowance must default to disabled (OFF)", manager.isAllowanceEnabled.value)
        assertEquals("Default daily limit must be 0 minutes", 0L, manager.dailyLimitMinutes.value)
        assertEquals("Default daily limit in ms must be 0", 0L, manager.dailyLimitMs)
        assertEquals(0L, manager.dailyAccumulatedMs.value)
        assertFalse("Allowance cannot be exhausted when disabled", manager.isAllowanceExhausted)
        assertEquals(ReelsAllowanceManager.AllowanceState.DISABLED, manager.allowanceState.value)
    }

    @Test
    fun testConfigurableLimitAndEnablement() {
        // Configure 15 minutes limit
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        assertTrue(manager.isAllowanceEnabled.value)
        assertEquals(15L, manager.dailyLimitMinutes.value)
        assertEquals(15 * 60 * 1000L, manager.dailyLimitMs)
        assertEquals(15 * 60 * 1000L, manager.allowanceRemainingMs)
        assertEquals(ReelsAllowanceManager.AllowanceState.PAUSED_WITHIN_ALLOWANCE, manager.allowanceState.value)

        // Disabling restores DISABLED state and 0 dailyLimitMs
        manager.setAllowanceEnabled(false)
        assertFalse(manager.isAllowanceEnabled.value)
        assertEquals(0L, manager.dailyLimitMs)
        assertEquals(ReelsAllowanceManager.AllowanceState.DISABLED, manager.allowanceState.value)
    }

    @Test
    fun testBoundedDeltasReusedWithoutDoubleCounting() {
        manager.setDailyLimitMinutes(30L)
        manager.setAllowanceEnabled(true)

        // t = 0s: Anchor observation (0ms credited)
        manager.onClassificationResult(createResult())
        assertEquals(0L, manager.accumulatedDailyTimeMs)
        assertEquals(ReelsAllowanceManager.AllowanceState.TRACKING_WITHIN_ALLOWANCE, manager.allowanceState.value)

        // 2s later (t = 2s): delta 2s <= 10s -> credits 2s
        currentTimeMs += 2_000L
        manager.onClassificationResult(createResult())
        assertEquals(2_000L, manager.accumulatedDailyTimeMs)

        // 10s later (t = 12s): delta 10s <= 10s -> credits 10s (total 12s)
        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        assertEquals(12_000L, manager.accumulatedDailyTimeMs)

        // 20s later (t = 32s): delta 20s > 10s -> capped at 10s (total 22s)
        currentTimeMs += 20_000L
        manager.onClassificationResult(createResult())
        assertEquals(22_000L, manager.accumulatedDailyTimeMs)

        // 30s later (t = 62s): delta 30s > 10s -> capped at 10s (total 32s)
        currentTimeMs += 30_000L
        manager.onClassificationResult(createResult())
        assertEquals(32_000L, manager.accumulatedDailyTimeMs)
    }

    @Test
    fun testTrailingIntervalOnScreenExit() {
        manager.setDailyLimitMinutes(30L)
        manager.setAllowanceEnabled(true)

        // t = 0s: Anchor on Reels
        manager.onClassificationResult(createResult())

        // t = 4s: User navigates away to Direct Messages
        currentTimeMs += 4_000L
        val dmResult = createResult(
            candidateState = CandidateScreenState.DM_CONVERSATION,
            confidence = Confidence.HIGH,
            reasonCode = ReasonCode.MULTIPLE_SIGNALS_MATCH
        )
        val isExhausted = manager.onClassificationResult(dmResult)

        assertFalse(isExhausted)
        // Bounded trailing interval of 4s must be credited
        assertEquals(4_000L, manager.accumulatedDailyTimeMs)
        assertEquals(ReelsAllowanceManager.AllowanceState.PAUSED_WITHIN_ALLOWANCE, manager.allowanceState.value)

        // Staying in DM for 10 minutes adds zero additional time
        currentTimeMs += 10 * 60 * 1000L
        manager.onClassificationResult(dmResult)
        assertEquals(4_000L, manager.accumulatedDailyTimeMs)
    }

    @Test
    fun testStaleObservationTimeoutSettlement() {
        manager.setDailyLimitMinutes(30L)
        manager.setAllowanceEnabled(true)

        // t = 0s: Anchor on Reels
        manager.onClassificationResult(createResult())

        // t = 6s: Mid-interval query returns dynamic lookahead (6s)
        currentTimeMs += 6_000L
        assertEquals(6_000L, manager.accumulatedDailyTimeMs)
        assertEquals(ReelsAllowanceManager.AllowanceState.TRACKING_WITHIN_ALLOWANCE, manager.allowanceState.value)

        // t = 15s: Stale timeout (10s) expired without new observations
        currentTimeMs += 9_000L // now 15s from anchor
        manager.syncState()

        // Settle at exactly 10s cap and transition to PAUSED
        assertEquals(10_000L, manager.accumulatedDailyTimeMs)
        assertEquals(ReelsAllowanceManager.AllowanceState.PAUSED_WITHIN_ALLOWANCE, manager.allowanceState.value)
    }

    @Test
    fun testLimitExhaustionAndStateTransition() {
        // Limit: 1 minute (60,000ms)
        manager.setDailyLimitMinutes(1L)
        manager.setAllowanceEnabled(true)

        // Anchor
        manager.onClassificationResult(createResult())

        // 30s elapsed
        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        assertEquals(30_000L, manager.accumulatedDailyTimeMs)
        assertEquals(30_000L, manager.allowanceRemainingMs)
        assertFalse(manager.isAllowanceExhausted)
        assertEquals(ReelsAllowanceManager.AllowanceState.TRACKING_WITHIN_ALLOWANCE, manager.allowanceState.value)

        // 30s more (reaching 60s total)
        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        currentTimeMs += 10_000L
        val requiresRestriction = manager.onClassificationResult(createResult())

        assertTrue("onClassificationResult must return true when allowance is exhausted on Reels", requiresRestriction)
        assertEquals(60_000L, manager.accumulatedDailyTimeMs)
        assertEquals(0L, manager.allowanceRemainingMs)
        assertTrue(manager.isAllowanceExhausted)
        assertEquals(ReelsAllowanceManager.AllowanceState.EXHAUSTED, manager.allowanceState.value)
    }

    @Test
    fun testDailyDateRolloverResetPolicy() {
        manager.setDailyLimitMinutes(10L)
        manager.setAllowanceEnabled(true)

        // Exhaust allowance on 2026-10-21
        manager.onClassificationResult(createResult())
        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        // Fast forward 10 minutes
        for (i in 1..59) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isAllowanceExhausted)
        assertEquals(ReelsAllowanceManager.AllowanceState.EXHAUSTED, manager.allowanceState.value)

        // Midnight arrives: date flips to 2026-10-22 while still active on Reels (1s elapsed)
        currentDate = "2026-10-22"
        currentTimeMs += 1_000L
        manager.syncState()

        // Daily accumulated time must reset to 0L and clear EXHAUSTED state
        assertEquals("Accumulated time must reset to 0 upon date change", 0L, manager.accumulatedDailyTimeMs)
        assertFalse("Allowance must no longer be exhausted after rollover", manager.isAllowanceExhausted)
        assertEquals(10 * 60 * 1000L, manager.allowanceRemainingMs)
        assertEquals("Still active on Reels at midnight, so state is TRACKING", ReelsAllowanceManager.AllowanceState.TRACKING_WITHIN_ALLOWANCE, manager.allowanceState.value)

        // After 15s of silence, stale timeout settles and transitions to PAUSED
        currentTimeMs += 15_000L
        manager.syncState()
        assertEquals(ReelsAllowanceManager.AllowanceState.PAUSED_WITHIN_ALLOWANCE, manager.allowanceState.value)
    }

    @Test
    fun testPersistenceAcrossSimulatedRestart() {
        manager.setDailyLimitMinutes(20L)
        manager.setAllowanceEnabled(true)

        // Accumulate 8 seconds
        manager.onClassificationResult(createResult())
        currentTimeMs += 8_000L
        manager.onClassificationResult(createResult())
        assertEquals(8_000L, manager.accumulatedDailyTimeMs)

        // Verify storage was updated
        assertEquals(8_000L, storage.loadDailyAccumulatedMs())
        assertTrue(storage.loadAllowanceEnabled())
        assertEquals(20L, storage.loadDailyLimitMinutes())
        assertEquals("2026-10-21", storage.loadStoredDate())

        // Simulate device reboot: monotonic clock resets to 0, new manager instance points to same storage
        val postRebootClockMs = 500L
        val restartedManager = ReelsAllowanceManager(
            clock = { postRebootClockMs },
            dateProvider = { currentDate },
            storage = storage,
            staleObservationTimeoutMs = 10_000L
        )

        assertTrue(restartedManager.isAllowanceEnabled.value)
        assertEquals(20L, restartedManager.dailyLimitMinutes.value)
        assertEquals(8_000L, restartedManager.accumulatedDailyTimeMs)
        assertEquals(ReelsAllowanceManager.AllowanceState.PAUSED_WITHIN_ALLOWANCE, restartedManager.allowanceState.value)
    }

    @Test
    fun testStrictNonReelsRejection() {
        manager.setDailyLimitMinutes(30L)
        manager.setAllowanceEnabled(true)

        // 1. Stories
        val storiesResult = createResult(candidateState = CandidateScreenState.STORIES)
        manager.onClassificationResult(storiesResult)
        assertEquals(0L, manager.accumulatedDailyTimeMs)

        // 2. Home Feed
        val feedResult = createResult(candidateState = CandidateScreenState.HOME_FEED)
        manager.onClassificationResult(feedResult)
        assertEquals(0L, manager.accumulatedDailyTimeMs)

        // 3. User Profile
        val profileResult = createResult(candidateState = CandidateScreenState.PROFILE)
        manager.onClassificationResult(profileResult)
        assertEquals(0L, manager.accumulatedDailyTimeMs)

        // 4. Low confidence
        val lowConfResult = createResult(confidence = Confidence.LOW)
        manager.onClassificationResult(lowConfResult)
        assertEquals(0L, manager.accumulatedDailyTimeMs)

        // 5. Partial tree
        val partialTreeResult = createResult(isPartialTree = true)
        manager.onClassificationResult(partialTreeResult)
        assertEquals(0L, manager.accumulatedDailyTimeMs)

        // 6. Non-Instagram package
        val externalPkgResult = createResult(isInstagramPackage = false)
        manager.onClassificationResult(externalPkgResult)
        assertEquals(0L, manager.accumulatedDailyTimeMs)
    }

    @Test
    fun testDuplicateObservationsHandling() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // First observation at t=0s
        manager.onClassificationResult(createResult())
        assertEquals(0L, manager.accumulatedDailyTimeMs)

        // Rapid duplicate events arriving with delta = 0ms (same timestamp)
        manager.onClassificationResult(createResult())
        manager.onClassificationResult(createResult())
        assertEquals("Duplicate events with zero delta must be handled idempotently", 0L, manager.accumulatedDailyTimeMs)

        // Event after 2s
        currentTimeMs += 2_000L
        manager.onClassificationResult(createResult())
        assertEquals(2_000L, manager.accumulatedDailyTimeMs)

        // Duplicate event at t=2s
        manager.onClassificationResult(createResult())
        assertEquals(2_000L, manager.accumulatedDailyTimeMs)
    }

    @Test
    fun testUnknownClassificationsHandling() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Anchor on Reels
        manager.onClassificationResult(createResult())
        currentTimeMs += 5_000L
        manager.onClassificationResult(createResult())
        assertEquals(5_000L, manager.accumulatedDailyTimeMs)

        // Unknown screen arrived (e.g. intermediate transition or obscured window)
        currentTimeMs += 3_000L
        val unknownResult = createResult(
            candidateState = CandidateScreenState.UNKNOWN,
            confidence = Confidence.LOW,
            reasonCode = ReasonCode.INSUFFICIENT_EVIDENCE
        )
        val requiresRestriction = manager.onClassificationResult(unknownResult)

        assertFalse("Unknown classification must never require restriction", requiresRestriction)
        // Trailing 3s on Reels credited before transition
        assertEquals(8_000L, manager.accumulatedDailyTimeMs)
        assertEquals(ReelsAllowanceManager.AllowanceState.PAUSED_WITHIN_ALLOWANCE, manager.allowanceState.value)

        // Staying in UNKNOWN state adds zero time
        currentTimeMs += 10_000L
        manager.onClassificationResult(unknownResult)
        assertEquals(8_000L, manager.accumulatedDailyTimeMs)
    }

    @Test
    fun testServiceDisconnectionAndSync() {
        manager.setDailyLimitMinutes(20L)
        manager.setAllowanceEnabled(true)

        // Active tracking on Reels
        manager.onClassificationResult(createResult())
        currentTimeMs += 4_000L
        manager.onClassificationResult(createResult())
        assertEquals(4_000L, manager.accumulatedDailyTimeMs)

        // Service disconnected (simulating onUnbind): syncState called
        currentTimeMs += 12_000L // 12s silence during disconnection
        manager.syncState()

        // Stale observation settles at 10s cap and transitions to PAUSED (4s + 10s = 14s)
        assertEquals(14_000L, manager.accumulatedDailyTimeMs)
        assertEquals(ReelsAllowanceManager.AllowanceState.PAUSED_WITHIN_ALLOWANCE, manager.allowanceState.value)

        // Later service reconnects: new observation resumes tracking from current time
        currentTimeMs += 60_000L // 1 minute later
        manager.onClassificationResult(createResult()) // Anchor
        currentTimeMs += 3_000L
        manager.onClassificationResult(createResult())
        assertEquals(17_000L, manager.accumulatedDailyTimeMs)
    }

    @Test
    fun testMultiSurfaceTransitions_Stories_Feed_Profile_DMs() {
        manager.setDailyLimitMinutes(20L)
        manager.setAllowanceEnabled(true)

        // 1. User on Reels for 6s
        manager.onClassificationResult(createResult())
        currentTimeMs += 6_000L
        manager.onClassificationResult(createResult())
        assertEquals(6_000L, manager.accumulatedDailyTimeMs)

        // 2. User taps Home Feed (transition out of Reels)
        currentTimeMs += 2_000L
        val feedResult = createResult(
            candidateState = CandidateScreenState.HOME_FEED,
            confidence = Confidence.MEDIUM,
            reasonCode = ReasonCode.MULTIPLE_SIGNALS_MATCH
        )
        assertFalse(manager.onClassificationResult(feedResult))
        assertEquals(8_000L, manager.accumulatedDailyTimeMs)
        assertEquals(ReelsAllowanceManager.AllowanceState.PAUSED_WITHIN_ALLOWANCE, manager.allowanceState.value)

        // 3. User taps Stories
        currentTimeMs += 5_000L
        val storiesResult = createResult(
            candidateState = CandidateScreenState.STORIES,
            confidence = Confidence.HIGH,
            reasonCode = ReasonCode.KNOWN_STRUCTURE_MATCH
        )
        assertFalse(manager.onClassificationResult(storiesResult))
        assertEquals(8_000L, manager.accumulatedDailyTimeMs)

        // 4. User views Profile
        currentTimeMs += 10_000L
        val profileResult = createResult(
            candidateState = CandidateScreenState.PROFILE,
            confidence = Confidence.HIGH,
            reasonCode = ReasonCode.KNOWN_STRUCTURE_MATCH
        )
        assertFalse(manager.onClassificationResult(profileResult))
        assertEquals(8_000L, manager.accumulatedDailyTimeMs)

        // 5. User returns to Reels
        currentTimeMs += 20_000L
        manager.onClassificationResult(createResult()) // Anchor (0ms added)
        assertEquals(8_000L, manager.accumulatedDailyTimeMs)
        assertEquals(ReelsAllowanceManager.AllowanceState.TRACKING_WITHIN_ALLOWANCE, manager.allowanceState.value)

        // 4s on Reels
        currentTimeMs += 4_000L
        manager.onClassificationResult(createResult())
        assertEquals(12_000L, manager.accumulatedDailyTimeMs)
    }
}
