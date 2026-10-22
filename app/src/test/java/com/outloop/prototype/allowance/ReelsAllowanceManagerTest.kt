package com.outloop.prototype.allowance

import com.outloop.classifier.CandidateScreenState
import com.outloop.classifier.ClassificationResult
import com.outloop.classifier.Confidence
import com.outloop.classifier.DiagnosticSummary
import com.outloop.classifier.ObservationOutcome
import com.outloop.classifier.ReasonCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    private var currentWallClockMs = 1_700_000_000_000L
    private var currentBootCount = 1
    private var currentDate = "2026-10-21"
    private var currentTimeZone = "UTC"
    private lateinit var storage: InMemoryAllowanceStorage
    private lateinit var historyStorage: com.outloop.prototype.history.InMemoryAttentionHistoryStorage
    private lateinit var historyRepo: com.outloop.prototype.history.AttentionHistoryRepository
    private lateinit var manager: ReelsAllowanceManager

    @Before
    fun setUp() {
        currentTimeMs = 1_000_000L
        currentWallClockMs = 1_700_000_000_000L
        currentBootCount = 1
        currentDate = "2026-10-21"
        currentTimeZone = "UTC"
        storage = InMemoryAllowanceStorage()
        historyStorage = com.outloop.prototype.history.InMemoryAttentionHistoryStorage()
        historyRepo = com.outloop.prototype.history.AttentionHistoryRepository(historyStorage)
        manager = ReelsAllowanceManager(
            clock = { currentTimeMs },
            wallClock = { currentWallClockMs },
            dateProvider = { currentDate },
            bootCountProvider = { currentBootCount },
            timeZoneProvider = { currentTimeZone },
            storage = storage,
            historyRepository = historyRepo,
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

    // ========================================================================
    // PHASE 4: SMART ATTENTION BUDGET & SPECIAL-DAY FLEXIBILITY TESTS
    // ========================================================================

    @Test
    fun testDefaultAllowanceRemainsUnchanged() {
        // Requirement 1: Default allowance remains unchanged
        assertFalse(manager.isAllowanceEnabled.value)
        assertEquals(0L, manager.dailyLimitMinutes.value)
        assertEquals(0L, manager.extraAllowanceMinutes.value)
        assertEquals(0L, manager.effectiveLimitMinutes.value)
        assertFalse(manager.isRestrictionRelaxed.value)
        assertNull(manager.specialDayReason.value)
        assertEquals(0L, manager.dailyAccumulatedMs.value)
        assertEquals(0L, manager.allowanceRemainingMs)
        assertFalse(manager.isAllowanceExhausted)
        assertEquals(ReelsAllowanceManager.AllowanceState.DISABLED, manager.allowanceState.value)
    }

    @Test
    fun testAddingExtraAllowanceChangesOnlyEffectiveAllowanceForToday() {
        // Requirement 2: Adding 10, 15, or 30 minutes changes only effective allowance for today
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        assertEquals("Normal limit must be 15m", 15L, manager.dailyLimitMinutes.value)
        assertEquals("Initial effective limit must be 15m", 15L, manager.effectiveLimitMinutes.value)

        // Add 10m
        assertTrue(manager.addExtraAllowance(10L))
        assertEquals("Normal limit must remain 15m", 15L, manager.dailyLimitMinutes.value)
        assertEquals("Extra allowance must be 10m", 10L, manager.extraAllowanceMinutes.value)
        assertEquals("Effective limit must be 25m", 25L, manager.effectiveLimitMinutes.value)

        // Add 15m (after debounce window)
        currentTimeMs += 1000L
        assertTrue(manager.addExtraAllowance(15L))
        assertEquals("Normal limit must remain 15m", 15L, manager.dailyLimitMinutes.value)
        assertEquals("Extra allowance must be 25m", 25L, manager.extraAllowanceMinutes.value)
        assertEquals("Effective limit must be 40m", 40L, manager.effectiveLimitMinutes.value)

        // Add 30m (after debounce window)
        currentTimeMs += 1000L
        assertTrue(manager.addExtraAllowance(30L))
        assertEquals("Normal limit must remain 15m", 15L, manager.dailyLimitMinutes.value)
        assertEquals("Extra allowance must be 55m", 55L, manager.extraAllowanceMinutes.value)
        assertEquals("Effective limit must be 70m", 70L, manager.effectiveLimitMinutes.value)
    }

    @Test
    fun testExtraAllowancePersistsAcrossAppRecreation() {
        // Requirement 3: Extra allowance persists after app process recreation
        manager.setDailyLimitMinutes(30L)
        manager.setAllowanceEnabled(true)
        manager.addExtraAllowance(15L, reason = SpecialDayReasons.HOLIDAY)

        assertEquals(15L, storage.loadExtraAllowanceMinutes())
        assertEquals(SpecialDayReasons.HOLIDAY, storage.loadSpecialDayReason())

        // Recreate manager with same storage on same day
        val postRecreationClockMs = 500L
        val recreatedManager = ReelsAllowanceManager(
            clock = { postRecreationClockMs },
            dateProvider = { currentDate },
            storage = storage,
            staleObservationTimeoutMs = 10_000L
        )

        assertTrue(recreatedManager.isAllowanceEnabled.value)
        assertEquals(30L, recreatedManager.dailyLimitMinutes.value)
        assertEquals(15L, recreatedManager.extraAllowanceMinutes.value)
        assertEquals(45L, recreatedManager.effectiveLimitMinutes.value)
        assertEquals(SpecialDayReasons.HOLIDAY, recreatedManager.specialDayReason.value)
    }

    @Test
    fun testExtraAllowanceDoesNotGrantRepeatedlyFromDuplicateActions() {
        // Requirement 4: Extra allowance does not grant repeatedly from duplicate actions
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // First grant at t = 1,000,000ms
        val firstGrant = manager.addExtraAllowance(15L)
        assertTrue("First grant must succeed", firstGrant)
        assertEquals(15L, manager.extraAllowanceMinutes.value)

        // Rapid duplicate call at exact same timestamp
        val duplicateSameTimestamp = manager.addExtraAllowance(15L)
        assertFalse("Duplicate call at same timestamp must be rejected", duplicateSameTimestamp)
        assertEquals("Extra allowance must remain 15L", 15L, manager.extraAllowanceMinutes.value)

        // Call 200ms later (within 500ms debounce window)
        currentTimeMs += 200L
        val duplicateWithinDebounce = manager.addExtraAllowance(15L)
        assertFalse("Duplicate call within debounce window must be rejected", duplicateWithinDebounce)
        assertEquals("Extra allowance must remain 15L", 15L, manager.extraAllowanceMinutes.value)

        // Call 600ms later (after debounce window)
        currentTimeMs += 600L
        val intentionalGrant = manager.addExtraAllowance(10L)
        assertTrue("Intentional grant after debounce window must succeed", intentionalGrant)
        assertEquals("Extra allowance must now be 25L", 25L, manager.extraAllowanceMinutes.value)
    }

    @Test
    fun testExistingUsagePreservedWhenExtraTimeGranted() {
        // Requirement 5: Existing usage is preserved when extra time is granted
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Accumulate 8 minutes of Reels usage
        manager.onClassificationResult(createResult())
        for (i in 1..48) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        val accumulatedBefore = manager.accumulatedDailyTimeMs
        assertEquals(8 * 60 * 1000L, accumulatedBefore)

        // Grant +15 minutes extra
        manager.addExtraAllowance(15L)

        // Accumulated usage must be completely intact
        assertEquals("Usage must be perfectly preserved after granting extra time", accumulatedBefore, manager.accumulatedDailyTimeMs)
        // Remaining time reflects new effective limit of 30m - 8m = 22m
        assertEquals((30 - 8) * 60 * 1000L, manager.allowanceRemainingMs)
    }

    @Test
    fun testRemainingTimeNeverBecomesNegative() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Accumulate up to and past 15m limit (120 * 10s = 20 mins)
        manager.onClassificationResult(createResult())
        for (i in 1..120) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        // Accounted daily usage stops at the effective limit boundary (15m)
        assertEquals("Usage must stop accounting after effective limit exhaustion", 15 * 60 * 1000L, manager.accumulatedDailyTimeMs)
        assertEquals("Remaining time must be 0L when limit is exhausted, never negative", 0L, manager.allowanceRemainingMs)

        // Add 10m extra (effective limit 25m, usage 15m) -> remaining 10m
        manager.addExtraAllowance(10L)
        assertEquals(10 * 60 * 1000L, manager.allowanceRemainingMs)

        // Accumulate 15 more minutes (exceeds new 25m limit)
        for (i in 1..90) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertEquals("Usage must clamp at new effective limit (25m)", 25 * 60 * 1000L, manager.accumulatedDailyTimeMs)
        assertEquals("Remaining time must clamp to 0L and never become negative", 0L, manager.allowanceRemainingMs)
    }

    @Test
    fun testRepeatedObservationsAfterExhaustionDoNotIncreaseCountedUsage() {
        manager.setDailyLimitMinutes(10L)
        manager.setAllowanceEnabled(true)

        // Watch exactly 10 minutes to exhaust allowance
        manager.onClassificationResult(createResult())
        for (i in 1..60) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }

        assertEquals(10 * 60 * 1000L, manager.accumulatedDailyTimeMs)
        assertEquals(0L, manager.allowanceRemainingMs)
        assertTrue(manager.isAllowanceExhausted)

        // Observe 50 more times over 500 seconds while overlay is visible on Reels
        for (i in 1..50) {
            currentTimeMs += 10_000L
            val restricted = manager.onClassificationResult(createResult())
            assertTrue("Restriction must continue to be required on Reels", restricted)
        }

        // Daily accumulated time must remain strictly clamped to 10 minutes
        assertEquals("Counted daily usage must not increase after exhaustion", 10 * 60 * 1000L, manager.accumulatedDailyTimeMs)
        assertEquals("Remaining allowance must remain strictly 0", 0L, manager.allowanceRemainingMs)
        assertEquals(ReelsAllowanceManager.AllowanceState.EXHAUSTED, manager.allowanceState.value)
    }

    @Test
    fun testRestrictionStaysActiveWhenUsageExceedsEffectiveAllowance() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)
        manager.relaxRestrictionForToday("Holiday")

        // Accumulate 35 minutes while relaxed
        manager.onClassificationResult(createResult())
        for (i in 1..210) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertEquals(35 * 60 * 1000L, manager.accumulatedDailyTimeMs)

        // User resumes normal restrictions
        manager.resumeNormalRestrictions()
        assertTrue(manager.isAllowanceExhausted)

        // User adds +10m extra allowance (effective limit becomes 25m)
        currentTimeMs += 1000L
        manager.addExtraAllowance(10L)

        // Usage (35m) still exceeds effective allowance (25m)
        assertTrue("Allowance must remain exhausted when usage exceeds effective limit", manager.isAllowanceExhausted)
        assertEquals(ReelsAllowanceManager.AllowanceState.EXHAUSTED, manager.allowanceState.value)
        val requiresRestriction = manager.onClassificationResult(createResult())
        assertTrue("Restriction must stay active on Reels when usage exceeds effective limit", requiresRestriction)
    }

    @Test
    fun testGrantingExtraAfterExhaustionUpdatesRestrictionStateAndDismissesOverlay() {
        // Requirement 8: Granting extra time after exhaustion updates restriction state correctly
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Exhaust the 15m allowance
        manager.onClassificationResult(createResult())
        for (i in 1..90) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isAllowanceExhausted)
        assertEquals(ReelsAllowanceManager.AllowanceState.EXHAUSTED, manager.allowanceState.value)

        // Track dismissal request
        var dismissalTriggered = false
        manager.onRestrictionDismissRequested = { dismissalTriggered = true }

        // Grant +15m extra time (effective limit becomes 30m, usage is 15m)
        manager.addExtraAllowance(15L)

        // Restriction state must immediately clear
        assertFalse("Allowance must no longer be exhausted after extra time granted", manager.isAllowanceExhausted)
        assertEquals(ReelsAllowanceManager.AllowanceState.TRACKING_WITHIN_ALLOWANCE, manager.allowanceState.value)
        assertEquals(15 * 60 * 1000L, manager.allowanceRemainingMs)
        assertTrue("Overlay dismissal must be requested immediately when restriction is lifted", dismissalTriggered)

        // Subsequent classification check does NOT require restriction
        val requiresRestriction = manager.onClassificationResult(createResult())
        assertFalse("Classification must not require restriction within effective allowance", requiresRestriction)
    }

    @Test
    fun testTemporaryRelaxationPreservesUsageHistory() {
        // Requirement 9: Temporary relaxation preserves usage history
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Accumulate 10 minutes of usage
        manager.onClassificationResult(createResult())
        for (i in 1..60) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertEquals(10 * 60 * 1000L, manager.accumulatedDailyTimeMs)

        // Relax restriction for today
        var dismissalTriggered = false
        manager.onRestrictionDismissRequested = { dismissalTriggered = true }
        assertTrue(manager.relaxRestrictionForToday("Holiday or festival"))

        assertTrue(manager.isRestrictionRelaxed.value)
        assertEquals("Holiday or festival", manager.specialDayReason.value)
        assertEquals(ReelsAllowanceManager.AllowanceState.RELAXED, manager.allowanceState.value)
        assertFalse("Relaxed day must never be exhausted", manager.isAllowanceExhausted)
        assertTrue(dismissalTriggered)

        // Usage history must be preserved
        assertEquals("Usage must be preserved upon relaxation", 10 * 60 * 1000L, manager.accumulatedDailyTimeMs)

        // Continue using Reels on relaxed day
        for (i in 1..30) { // 5 more minutes
            currentTimeMs += 10_000L
            val requiresRestriction = manager.onClassificationResult(createResult())
            assertFalse("Relaxed day must not trigger restriction", requiresRestriction)
        }
        // Total usage correctly tracks to 15m without blocking
        assertEquals(15 * 60 * 1000L, manager.accumulatedDailyTimeMs)
    }

    @Test
    fun testTemporaryRelaxationPersistsAcrossProcessRecreationAndReboot() {
        // Requirement 10: Temporary relaxation persists across process recreation and reboot-compatible storage
        manager.setDailyLimitMinutes(20L)
        manager.setAllowanceEnabled(true)
        manager.relaxRestrictionForToday(SpecialDayReasons.VACATION)

        assertTrue(storage.loadRestrictionRelaxed())
        assertEquals(SpecialDayReasons.VACATION, storage.loadSpecialDayReason())

        // Recreate manager on same day
        val postRebootClockMs = 200L
        val recreatedManager = ReelsAllowanceManager(
            clock = { postRebootClockMs },
            dateProvider = { currentDate },
            storage = storage,
            staleObservationTimeoutMs = 10_000L
        )

        assertTrue(recreatedManager.isRestrictionRelaxed.value)
        assertEquals(SpecialDayReasons.VACATION, recreatedManager.specialDayReason.value)
        assertEquals(ReelsAllowanceManager.AllowanceState.RELAXED, recreatedManager.allowanceState.value)
        assertFalse(recreatedManager.isAllowanceExhausted)
    }

    @Test
    fun testExpiredSpecialDayStateClearedOnNextLocalDay() {
        // Requirement 11: Expired special-day state is cleared on the next local day
        manager.setDailyLimitMinutes(20L)
        manager.setAllowanceEnabled(true)
        manager.addExtraAllowance(30L, reason = SpecialDayReasons.CELEBRATION)
        manager.relaxRestrictionForToday(SpecialDayReasons.CELEBRATION)

        assertEquals(30L, manager.extraAllowanceMinutes.value)
        assertTrue(manager.isRestrictionRelaxed.value)
        assertEquals(SpecialDayReasons.CELEBRATION, manager.specialDayReason.value)

        // Rollover to next day
        currentDate = "2026-10-22"
        currentTimeMs += 1000L
        manager.syncState()

        assertEquals("Extra allowance must reset to 0 upon day rollover", 0L, manager.extraAllowanceMinutes.value)
        assertFalse("Restriction relaxation must clear upon day rollover", manager.isRestrictionRelaxed.value)
        assertNull("Special-day reason must clear upon day rollover", manager.specialDayReason.value)
        assertEquals("Accumulated daily time must reset to 0 upon day rollover", 0L, manager.accumulatedDailyTimeMs)

        // Storage must also be cleared
        assertEquals(0L, storage.loadExtraAllowanceMinutes())
        assertFalse(storage.loadRestrictionRelaxed())
        assertNull(storage.loadSpecialDayReason())
    }

    @Test
    fun testNormalConfiguredAllowanceUnchangedAfterExpiration() {
        // Requirement 12: The normal configured allowance remains unchanged after expiration
        manager.setDailyLimitMinutes(45L)
        manager.setAllowanceEnabled(true)
        manager.addExtraAllowance(15L)

        assertEquals(45L, manager.dailyLimitMinutes.value)
        assertEquals(60L, manager.effectiveLimitMinutes.value)

        // Next calendar day arrives
        currentDate = "2026-10-22"
        currentTimeMs += 5000L
        manager.syncState()

        assertEquals("Normal configured limit must remain 45m after special day expires", 45L, manager.dailyLimitMinutes.value)
        assertEquals("Effective limit must equal normal limit (45m) after expiration", 45L, manager.effectiveLimitMinutes.value)
        assertTrue("Allowance remains enabled", manager.isAllowanceEnabled.value)
    }

    @Test
    fun testReasonsAreOptionalAndDoNotAffectEligibility() {
        // Requirement 13: Reasons are optional and do not affect eligibility
        val managerNoReason = ReelsAllowanceManager(
            clock = { currentTimeMs },
            dateProvider = { currentDate },
            storage = InMemoryAllowanceStorage()
        )
        managerNoReason.setDailyLimitMinutes(15L)
        managerNoReason.setAllowanceEnabled(true)
        managerNoReason.addExtraAllowance(15L, reason = null)

        val managerWithPreset = ReelsAllowanceManager(
            clock = { currentTimeMs },
            dateProvider = { currentDate },
            storage = InMemoryAllowanceStorage()
        )
        managerWithPreset.setDailyLimitMinutes(15L)
        managerWithPreset.setAllowanceEnabled(true)
        managerWithPreset.addExtraAllowance(15L, reason = SpecialDayReasons.PERSONAL)

        val managerWithCustom = ReelsAllowanceManager(
            clock = { currentTimeMs },
            dateProvider = { currentDate },
            storage = InMemoryAllowanceStorage()
        )
        managerWithCustom.setDailyLimitMinutes(15L)
        managerWithCustom.setAllowanceEnabled(true)
        managerWithCustom.addExtraAllowance(15L, reason = "Custom personal occasion")

        // In all cases, allowance math and eligibility are mathematically identical
        assertEquals(managerNoReason.effectiveLimitMinutes.value, managerWithPreset.effectiveLimitMinutes.value)
        assertEquals(managerPresetReasonEqualsCustom(managerWithPreset, managerWithCustom), true)
        assertEquals(managerNoReason.allowanceRemainingMs, managerWithCustom.allowanceRemainingMs)
        assertEquals(managerNoReason.isAllowanceExhausted, managerWithCustom.isAllowanceExhausted)
    }

    private fun managerPresetReasonEqualsCustom(a: ReelsAllowanceManager, b: ReelsAllowanceManager): Boolean {
        return a.effectiveLimitMinutes.value == b.effectiveLimitMinutes.value &&
               a.effectiveLimitMs == b.effectiveLimitMs
    }

    @Test
    fun testChangingNormalAllowanceDuringSpecialDayIsHandledConsistently() {
        // Requirement 14: Changing the normal allowance during a special day is handled consistently
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)
        manager.addExtraAllowance(15L) // Effective: 30m

        // Accumulate 20m of usage
        manager.onClassificationResult(createResult())
        for (i in 1..120) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertEquals(20 * 60 * 1000L, manager.accumulatedDailyTimeMs)
        assertEquals(10 * 60 * 1000L, manager.allowanceRemainingMs)
        assertFalse(manager.isAllowanceExhausted)

        // Change normal allowance to 30m
        manager.setDailyLimitMinutes(30L)
        assertEquals(30L, manager.dailyLimitMinutes.value)
        assertEquals(15L, manager.extraAllowanceMinutes.value)
        assertEquals("Effective limit must become 45m (30 normal + 15 extra)", 45L, manager.effectiveLimitMinutes.value)
        assertEquals("Usage must be preserved at 20m", 20 * 60 * 1000L, manager.accumulatedDailyTimeMs)
        assertEquals("Remaining time must now be 25m (45 - 20)", 25 * 60 * 1000L, manager.allowanceRemainingMs)
        assertFalse(manager.isAllowanceExhausted)

        // Change normal allowance down to 0m (disabled limit)
        manager.setDailyLimitMinutes(0L)
        assertEquals(0L, manager.dailyLimitMinutes.value)
        assertEquals("Effective limit is only the extra time (15m)", 15L, manager.effectiveLimitMinutes.value)
        // Since usage is 20m and effective limit is 15m, it is now exhausted!
        assertTrue("Allowance is now exhausted as usage (20m) > effective limit (15m)", manager.isAllowanceExhausted)
        assertEquals(0L, manager.allowanceRemainingMs)
    }

    @Test
    fun testResumeNormalRestrictionsClearsRelaxation() {
        // Extra test: Canceling relaxation re-engages restrictions if limit is exceeded
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Accumulate 20m (exhausted)
        manager.onClassificationResult(createResult())
        for (i in 1..120) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isAllowanceExhausted)

        // Relax restriction
        currentTimeMs += 1000L
        manager.relaxRestrictionForToday()
        assertFalse(manager.isAllowanceExhausted)
        assertEquals(ReelsAllowanceManager.AllowanceState.RELAXED, manager.allowanceState.value)

        // Resume normal restrictions
        currentTimeMs += 1000L
        manager.resumeNormalRestrictions()
        assertFalse(manager.isRestrictionRelaxed.value)
        assertTrue("Allowance must immediately return to EXHAUSTED after resuming restrictions", manager.isAllowanceExhausted)
        assertEquals(ReelsAllowanceManager.AllowanceState.EXHAUSTED, manager.allowanceState.value)
    }

    // =========================================================================
    // PHASE 5: CLOCK INTEGRITY & ANTI-TAMPERING TEST SUITE
    // =========================================================================

    @Test
    fun testForwardJumpOfHoursDetectsDiscrepancyAndRejectsReset() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Exhaust allowance (15m = 90 observations of 10s)
        manager.onClassificationResult(createResult())
        for (i in 1..90) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue("Allowance must be exhausted", manager.isAllowanceExhausted)

        // Step away from Reels before clock tampering
        manager.onClassificationResult(createResult(isInstagramPackage = false))

        // User jumps wall clock forward 5 hours while monotonic time only advances 5s
        currentTimeMs += 5_000L
        currentWallClockMs += 5 * 3600_000L
        manager.syncState()

        assertTrue("Clock discrepancy must be detected on forward jump", manager.isClockDiscrepancyDetected.value)
        assertTrue("Allowance must remain exhausted despite forward clock jump", manager.isAllowanceExhausted)
        assertEquals(15 * 60 * 1000L, manager.accumulatedDailyTimeMs)
    }

    @Test
    fun testForwardJumpOfOneDayAndSeveralDaysRejectsReset() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Exhaust allowance
        manager.onClassificationResult(createResult())
        for (i in 1..90) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isAllowanceExhausted)

        // Step away from Reels before clock tampering
        manager.onClassificationResult(createResult(isInstagramPackage = false))

        // 1. Forward jump of 1 day (user changes system date in Settings without reboot)
        currentTimeMs += 1000L
        currentWallClockMs += 86_400_000L
        currentDate = "2026-10-22"
        manager.syncState()

        assertTrue("Discrepancy must be flagged on 1-day jump", manager.isClockDiscrepancyDetected.value)
        assertTrue("Daily reset must be rejected; allowance must remain exhausted", manager.isAllowanceExhausted)
        assertEquals("Usage must be preserved", 15 * 60 * 1000L, manager.accumulatedDailyTimeMs)

        // 2. Forward jump of several days (e.g. 4 days later)
        currentTimeMs += 1000L
        currentWallClockMs += 3 * 86_400_000L
        currentDate = "2026-10-25"
        manager.syncState()

        assertTrue("Discrepancy must remain flagged", manager.isClockDiscrepancyDetected.value)
        assertTrue("Allowance must remain exhausted across several-day jump", manager.isAllowanceExhausted)
    }

    @Test
    fun testBackwardJumpAcrossMidnightDetectsDiscrepancyAndPreservesState() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Exhaust allowance
        manager.onClassificationResult(createResult())
        for (i in 1..90) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isAllowanceExhausted)

        // Step away from Reels before clock tampering
        manager.onClassificationResult(createResult(isInstagramPackage = false))

        // Backward jump across midnight (e.g. user sets clock back to 23:00 yesterday)
        currentTimeMs += 1000L
        currentWallClockMs -= 12 * 3600_000L
        currentDate = "2026-10-20"
        manager.syncState()

        assertTrue("Backward clock jump must be detected", manager.isClockDiscrepancyDetected.value)
        assertTrue("Allowance must remain exhausted", manager.isAllowanceExhausted)
        assertEquals(15 * 60 * 1000L, manager.accumulatedDailyTimeMs)
    }

    @Test
    fun testRepeatedForwardAndBackwardClockChanges() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Accumulate 10m
        manager.onClassificationResult(createResult())
        for (i in 1..60) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        // Step away from Reels before clock tampering
        manager.onClassificationResult(createResult(isInstagramPackage = false))
        assertEquals(10 * 60 * 1000L, manager.accumulatedDailyTimeMs)

        // Forward jump
        currentTimeMs += 1000L
        currentWallClockMs += 4 * 3600_000L
        manager.syncState()
        assertTrue(manager.isClockDiscrepancyDetected.value)

        // Backward jump
        currentTimeMs += 1000L
        currentWallClockMs -= 8 * 3600_000L
        manager.syncState()
        assertTrue(manager.isClockDiscrepancyDetected.value)

        // Forward jump again
        currentTimeMs += 1000L
        currentWallClockMs += 10 * 3600_000L
        manager.syncState()
        assertTrue(manager.isClockDiscrepancyDetected.value)

        // Accumulated usage must never be cleared or corrupted
        assertEquals("Accumulated usage must be preserved across repeated jumps", 10 * 60 * 1000L, manager.accumulatedDailyTimeMs)
    }

    @Test
    fun testClockChangesAfterAllowanceExhaustionDoesNotUnlockReels() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Reach exhaustion
        manager.onClassificationResult(createResult())
        for (i in 1..90) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isAllowanceExhausted)

        // User attempts backward jump to undo exhaustion
        currentTimeMs += 1000L
        currentWallClockMs -= 2 * 3600_000L
        val requiresRestriction = manager.onClassificationResult(createResult())

        assertTrue("Restriction must still be required on confirmed Reels screen", requiresRestriction)
        assertTrue("Allowance must remain exhausted", manager.isAllowanceExhausted)
    }

    @Test
    fun testClockChangesDuringExtraAllowancePreservesExtraTime() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Grant extra allowance
        assertTrue(manager.addExtraAllowance(15L, "Vacation"))
        assertEquals(30L, manager.effectiveLimitMinutes.value)
        assertEquals(15L, manager.extraAllowanceMinutes.value)

        // User manipulates clock
        currentTimeMs += 2000L
        currentWallClockMs += 3 * 3600_000L
        manager.syncState()

        assertEquals("Extra allowance must be preserved during clock discrepancy", 15L, manager.extraAllowanceMinutes.value)
        assertEquals(30L, manager.effectiveLimitMinutes.value)
        assertTrue(manager.isClockDiscrepancyDetected.value)
    }

    @Test
    fun testClockChangesDuringRelaxTodayPreservesRelaxationState() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Relax today
        assertTrue(manager.relaxRestrictionForToday("Personal day"))
        assertTrue(manager.isRestrictionRelaxed.value)

        // Clock manipulation
        currentTimeMs += 2000L
        currentWallClockMs -= 4 * 3600_000L
        manager.syncState()

        assertTrue("Relaxation must be preserved for today despite clock jump", manager.isRestrictionRelaxed.value)
        assertTrue(manager.isClockDiscrepancyDetected.value)
    }

    @Test
    fun testProcessRecreationRestoresTimingAnchorsAndUsage() {
        manager.setDailyLimitMinutes(30L)
        manager.setAllowanceEnabled(true)

        // Accumulate 15m
        manager.onClassificationResult(createResult())
        for (i in 1..90) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertEquals(15 * 60 * 1000L, manager.accumulatedDailyTimeMs)

        // Simulate process death and recreation with same storage
        currentTimeMs += 20_000L
        currentWallClockMs += 20_000L
        val recreatedManager = ReelsAllowanceManager(
            clock = { currentTimeMs },
            wallClock = { currentWallClockMs },
            dateProvider = { currentDate },
            bootCountProvider = { currentBootCount },
            timeZoneProvider = { currentTimeZone },
            storage = storage,
            historyRepository = historyRepo,
            staleObservationTimeoutMs = 10_000L
        )

        assertEquals("Usage must be restored after process recreation", 15 * 60 * 1000L, recreatedManager.accumulatedDailyTimeMs)
        assertEquals(30L, recreatedManager.dailyLimitMinutes.value)
        assertFalse("No discrepancy if elapsed monotonic and wall clock align across process death", recreatedManager.isClockDiscrepancyDetected.value)
    }

    @Test
    fun testRebootWithChangedWallClock() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Accumulate 15m (exhausted)
        manager.onClassificationResult(createResult())
        for (i in 1..90) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isAllowanceExhausted)

        // Case A: Reboot with wall clock moved backward
        currentBootCount = 2
        currentTimeMs = 5_000L // monotonic clock reset to 5s after boot
        currentWallClockMs -= 3600_000L // wall clock moved back 1 hour
        manager.syncState()

        assertTrue("Backward jump across reboot must be detected", manager.isClockDiscrepancyDetected.value)
        assertTrue("Allowance must remain exhausted after suspicious reboot", manager.isAllowanceExhausted)

        // Case B: Legitimate reboot overnight (clock advances 8 hours, boot count incremented)
        val freshStorage = InMemoryAllowanceStorage()
        val m1 = ReelsAllowanceManager(
            clock = { 500_000L },
            wallClock = { 1_700_000_000_000L },
            dateProvider = { "2026-10-21" },
            bootCountProvider = { 1 },
            storage = freshStorage,
            historyRepository = historyRepo
        )
        m1.setDailyLimitMinutes(15L)
        m1.setAllowanceEnabled(true)

        // Next morning after reboot:
        val m2 = ReelsAllowanceManager(
            clock = { 20_000L }, // fresh boot monotonic time
            wallClock = { 1_700_000_000_000L + 8 * 3600_000L }, // 8 hours later
            dateProvider = { "2026-10-22" }, // new day
            bootCountProvider = { 2 },
            storage = freshStorage,
            historyRepository = historyRepo
        )

        assertFalse("Legitimate overnight reboot must not flag discrepancy", m2.isClockDiscrepancyDetected.value)
        assertEquals("Fresh daily allowance starts at 0ms", 0L, m2.accumulatedDailyTimeMs)
        assertEquals("Normal limit preserved", 15L, m2.dailyLimitMinutes.value)
        assertFalse("Allowance not exhausted on fresh day", m2.isAllowanceExhausted)
    }

    @Test
    fun testLegitimateMidnightRolloverWhileRunning() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Accumulate 15m (exhausted)
        manager.onClassificationResult(createResult())
        for (i in 1..90) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isAllowanceExhausted)
        manager.addExtraAllowance(10L)
        assertEquals(25L, manager.effectiveLimitMinutes.value)

        // Time rolls over midnight normally (both monotonic and wall clock advance by 2 minutes)
        currentTimeMs += 120_000L
        currentWallClockMs += 120_000L
        currentDate = "2026-10-22"
        manager.syncState()

        assertFalse("Legitimate midnight rollover must not flag discrepancy", manager.isClockDiscrepancyDetected.value)
        assertEquals("Fresh day usage starts at 0ms", 0L, manager.accumulatedDailyTimeMs)
        assertEquals("Extra allowance cleared on midnight rollover", 0L, manager.extraAllowanceMinutes.value)
        assertEquals("Normal limit preserved", 15L, manager.dailyLimitMinutes.value)
        assertFalse("Allowance not exhausted on fresh day", manager.isAllowanceExhausted)
    }

    @Test
    fun testTimezoneChangeDoesNotResetAllowancePrematurely() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Exhaust allowance
        manager.onClassificationResult(createResult())
        for (i in 1..90) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isAllowanceExhausted)

        // Step away from Reels before timezone shift
        manager.onClassificationResult(createResult(isInstagramPackage = false))

        // User shifts timezone across date boundary (e.g. into tomorrow) without real time passing
        currentTimeZone = "Asia/Tokyo"
        currentTimeMs += 2000L
        currentWallClockMs += 2000L
        currentDate = "2026-10-22"
        manager.syncState()

        assertTrue("Timezone shift across date boundary without elapsed time must be flagged", manager.isClockDiscrepancyDetected.value)
        assertTrue("Allowance must remain exhausted; reset rejected", manager.isAllowanceExhausted)
        assertEquals(15 * 60 * 1000L, manager.accumulatedDailyTimeMs)
    }

    @Test
    fun testUsageMeasurementRemainsCorrectDespiteWallClockChanges() {
        manager.setDailyLimitMinutes(30L)
        manager.setAllowanceEnabled(true)

        // Start tracking
        manager.onClassificationResult(createResult())

        // 10s passes monotonically, but wall clock jumps backward 2 hours
        currentTimeMs += 10_000L
        currentWallClockMs -= 2 * 3600_000L
        manager.onClassificationResult(createResult())

        assertEquals("Usage must advance by exactly 10s monotonic slice despite wall-clock backward jump", 10_000L, manager.accumulatedDailyTimeMs)

        // Another 10s passes monotonically, wall clock jumps forward 4 hours
        currentTimeMs += 10_000L
        currentWallClockMs += 4 * 3600_000L
        manager.onClassificationResult(createResult())

        assertEquals("Usage must advance by another 10s monotonic slice (total 20s)", 20_000L, manager.accumulatedDailyTimeMs)
    }

    @Test
    fun testSafeRecoveryAfterSuspiciousClockChange() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Accumulate 10m
        manager.onClassificationResult(createResult())
        for (i in 1..60) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        // Step away from Reels before clock manipulation
        manager.onClassificationResult(createResult(isInstagramPackage = false))

        val anchorWall = currentWallClockMs

        // User moves clock backward 1 hour
        currentTimeMs += 1000L
        currentWallClockMs -= 3600_000L
        manager.syncState()
        assertTrue("Discrepancy detected", manager.isClockDiscrepancyDetected.value)

        // User returns clock to correct network time
        currentTimeMs += 1000L
        currentWallClockMs = anchorWall + 2000L
        manager.syncState()

        assertFalse("Discrepancy must safely resolve when clock normalizes", manager.isClockDiscrepancyDetected.value)
        assertEquals("Usage preserved through discrepancy and recovery", 10 * 60 * 1000L, manager.accumulatedDailyTimeMs)
        assertEquals(15L, manager.dailyLimitMinutes.value)
    }

    // ========================================================================
    // CUSTOM DAILY LIMIT TESTS
    // ========================================================================

    @Test
    fun testCustomShortDailyLimitsWork1m2m3m() {
        for (mins in listOf(1L, 2L, 3L)) {
            manager.setDailyLimitMinutes(mins)
            manager.setAllowanceEnabled(true)
            assertEquals(mins, manager.dailyLimitMinutes.value)
            assertEquals(mins * 60 * 1000L, manager.allowanceRemainingMs)

            // Step onto Reels and accumulate exactly the limit
            manager.onClassificationResult(createResult())
            val slices = (mins * 60) / 10
            for (i in 1..slices) {
                currentTimeMs += 10_000L
                currentWallClockMs += 10_000L
                manager.onClassificationResult(createResult())
            }

            assertTrue("Limit of ${mins}m must be exhausted after ${mins} minutes", manager.isAllowanceExhausted)
            assertEquals(0L, manager.allowanceRemainingMs)

            // Reset for next test iteration
            manager.setAllowanceEnabled(false)
            currentDate = "2026-10-${22 + mins.toInt()}"
            currentTimeMs += 1000L
            currentWallClockMs += 1000L
            manager.syncState()
        }
    }

    @Test
    fun testChangingLimitPreservesAccumulatedUsageAndExtraAllowance() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)
        manager.addExtraAllowance(5L, "Study")

        // Accumulate 10 minutes of Reels usage
        manager.onClassificationResult(createResult())
        for (i in 1..60) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertEquals(10 * 60 * 1000L, manager.accumulatedDailyTimeMs)
        assertEquals(5L, manager.extraAllowanceMinutes.value)

        // Change configured limit to custom 45 minutes
        manager.setDailyLimitMinutes(45L)

        // Verify accumulated usage and extra allowance are completely preserved
        assertEquals("Accumulated daily usage must not change when daily limit is modified", 10 * 60 * 1000L, manager.accumulatedDailyTimeMs)
        assertEquals("Extra allowance minutes must not be lost or changed", 5L, manager.extraAllowanceMinutes.value)
        assertEquals("Configured limit must reflect new custom value", 45L, manager.dailyLimitMinutes.value)
        assertEquals("Effective limit must be 45m normal + 5m extra = 50m", 50L, manager.effectiveLimitMinutes.value)

        // Remaining time must be 50m - 10m = 40m
        assertEquals(40 * 60 * 1000L, manager.allowanceRemainingMs)
        assertFalse(manager.isAllowanceExhausted)
    }

    @Test
    fun testLoweringLimitBelowCurrentUsageImmediatelyExhaustsAllowance() {
        manager.setDailyLimitMinutes(30L)
        manager.setAllowanceEnabled(true)

        // Accumulate 20 minutes
        manager.onClassificationResult(createResult())
        for (i in 1..120) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertEquals(20 * 60 * 1000L, manager.accumulatedDailyTimeMs)
        assertFalse(manager.isAllowanceExhausted)

        // Lower limit to custom 15m (below the 20m already watched)
        manager.setDailyLimitMinutes(15L)

        // Allowance must be immediately exhausted
        assertTrue("Lowering limit below today's accumulated usage must immediately exhaust allowance", manager.isAllowanceExhausted)
        assertEquals(0L, manager.allowanceRemainingMs)

        // Next observation requires restriction
        val requiresRestriction = manager.onClassificationResult(createResult())
        assertTrue("Subsequent observation must require restriction", requiresRestriction)
    }

    @Test
    fun testRaisingLimitAboveExhaustedUsageDismissesRestriction() {
        manager.setDailyLimitMinutes(15L)
        manager.setAllowanceEnabled(true)

        // Exhaust allowance (15m)
        manager.onClassificationResult(createResult())
        for (i in 1..90) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isAllowanceExhausted)

        var dismissalRequested = false
        manager.onRestrictionDismissRequested = { dismissalRequested = true }

        // Raise limit to custom 30m
        manager.setDailyLimitMinutes(30L)

        // Restriction dismissal callback must be fired
        assertTrue("Raising limit above accumulated usage must request restriction dismissal", dismissalRequested)
        assertFalse("Allowance must no longer be exhausted", manager.isAllowanceExhausted)
        assertEquals(15 * 60 * 1000L, manager.allowanceRemainingMs)
    }

    @Test
    fun testCustomTwoMinuteDailyLimitAppliesAndPersistsAcrossRestarts() {
        // Initial setup: enabled with 15m preset
        manager.setAllowanceEnabled(true)
        manager.setDailyLimitMinutes(15L)
        manager.addExtraAllowance(5L, "Study break")
        assertEquals(15L, manager.dailyLimitMinutes.value)
        assertEquals(20L, manager.effectiveLimitMinutes.value)

        // Accumulate 1 minute (60_000 ms)
        manager.onClassificationResult(createResult())
        for (i in 1..6) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertEquals(60_000L, manager.accumulatedDailyTimeMs)
        assertFalse(manager.isAllowanceExhausted)

        // Apply Custom 2-minute limit
        manager.setDailyLimitMinutes(2L)

        // 1. Immediately reflects 2m
        assertEquals(2L, manager.dailyLimitMinutes.value)
        // 2. Persisted to storage
        assertEquals(2L, storage.loadDailyLimitMinutes())
        // 3. Extra allowance, accumulated usage, and clock state preserved
        assertEquals(5L, manager.extraAllowanceMinutes.value)
        assertEquals(7L, manager.effectiveLimitMinutes.value) // 2m + 5m = 7m
        assertEquals(60_000L, manager.accumulatedDailyTimeMs) // 1m preserved
        assertFalse(manager.isClockDiscrepancyDetected.value)

        // 4. Simulate app restart: re-instantiate manager from storage
        val restartedManager = ReelsAllowanceManager(
            clock = { currentTimeMs },
            wallClock = { currentWallClockMs },
            dateProvider = { currentDate },
            bootCountProvider = { currentBootCount },
            timeZoneProvider = { currentTimeZone },
            storage = storage,
            historyRepository = historyRepo,
            staleObservationTimeoutMs = 10_000L
        )
        assertEquals(2L, restartedManager.dailyLimitMinutes.value)
        assertEquals(5L, restartedManager.extraAllowanceMinutes.value)
        assertEquals(7L, restartedManager.effectiveLimitMinutes.value)
        assertEquals(60_000L, restartedManager.accumulatedDailyTimeMs)

        // 5. Without extra allowance: test that 2m limit activates restriction after 2 minutes
        storage.saveExtraAllowanceMinutes(0L)
        val strictManager = ReelsAllowanceManager(
            clock = { currentTimeMs },
            wallClock = { currentWallClockMs },
            dateProvider = { currentDate },
            bootCountProvider = { currentBootCount },
            timeZoneProvider = { currentTimeZone },
            storage = storage,
            historyRepository = historyRepo,
            staleObservationTimeoutMs = 10_000L
        )
        assertEquals(2L, strictManager.dailyLimitMinutes.value)
        assertEquals(2L, strictManager.effectiveLimitMinutes.value)

        // Accumulate another 60 seconds (reaching 2 minutes total)
        strictManager.onClassificationResult(createResult())
        for (i in 1..6) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            val restricted = strictManager.onClassificationResult(createResult())
            if (i == 6) {
                assertTrue("2-minute limit reached must require restriction", restricted)
            }
        }
        assertTrue("Allowance must be exhausted at 2 minutes", strictManager.isAllowanceExhausted)
        assertEquals(0L, strictManager.allowanceRemainingMs)
    }

    // ========================================================================
    // EXTRA ALLOWANCE REMOVAL & UNDO TESTS
    // ========================================================================

    @Test
    fun testAddExtraAllowanceAndUndoLastAddition() {
        manager.setAllowanceEnabled(true)
        manager.setDailyLimitMinutes(15L)
        assertEquals(15L, manager.dailyLimitMinutes.value)
        assertEquals(0L, manager.extraAllowanceMinutes.value)

        // Add +10 minutes
        assertTrue(manager.addExtraAllowance(10L, "First break"))
        assertEquals(10L, manager.extraAllowanceMinutes.value)
        assertEquals(25L, manager.effectiveLimitMinutes.value)
        assertEquals(listOf(10L), manager.extraAllowanceHistory.value)

        // Advance time to bypass debounce, add +15 minutes
        currentTimeMs += 60_000L
        assertTrue(manager.addExtraAllowance(15L, "Second break"))
        assertEquals(25L, manager.extraAllowanceMinutes.value)
        assertEquals(40L, manager.effectiveLimitMinutes.value)
        assertEquals(listOf(10L, 15L), manager.extraAllowanceHistory.value)

        // Undo last addition (+15m)
        assertTrue(manager.undoLastExtraAllowance())
        assertEquals("Undoing last addition must restore extra allowance to 10m", 10L, manager.extraAllowanceMinutes.value)
        assertEquals("Effective limit must restore to 25m", 25L, manager.effectiveLimitMinutes.value)
        assertEquals(listOf(10L), manager.extraAllowanceHistory.value)

        // Undo again (+10m)
        assertTrue(manager.undoLastExtraAllowance())
        assertEquals("Undoing second addition must restore extra allowance to 0m", 0L, manager.extraAllowanceMinutes.value)
        assertEquals("Effective limit must restore to base 15m", 15L, manager.effectiveLimitMinutes.value)
        assertTrue(manager.extraAllowanceHistory.value.isEmpty())

        // Undo when no extra time is active
        assertFalse("Undoing when extra allowance is 0m must return false", manager.undoLastExtraAllowance())
        assertEquals(15L, manager.dailyLimitMinutes.value)
        assertEquals(15L, manager.effectiveLimitMinutes.value)
    }

    @Test
    fun testRemoveAllExtraTimeRestoresBaseLimit() {
        manager.setAllowanceEnabled(true)
        manager.setDailyLimitMinutes(30L)
        manager.addExtraAllowance(15L, "Study session")
        assertEquals(45L, manager.effectiveLimitMinutes.value)
        assertEquals(15L, manager.extraAllowanceMinutes.value)

        // Remove all extra time
        assertTrue(manager.removeAllExtraAllowance())
        assertEquals("Extra allowance must be 0m after removing all extra time", 0L, manager.extraAllowanceMinutes.value)
        assertEquals("Effective limit must restore to normal daily limit (30m)", 30L, manager.effectiveLimitMinutes.value)
        assertEquals("Normal daily limit must not be changed", 30L, manager.dailyLimitMinutes.value)
        assertTrue(manager.extraAllowanceHistory.value.isEmpty())

        // Removing all extra time again returns false
        assertFalse("Removing all extra when already 0m must return false", manager.removeAllExtraAllowance())
    }

    @Test
    fun testExtraAllowanceRemovalPersistsAcrossRestart() {
        manager.setAllowanceEnabled(true)
        manager.setDailyLimitMinutes(20L)
        manager.addExtraAllowance(30L, "Special occasion")
        assertEquals(50L, manager.effectiveLimitMinutes.value)

        // Remove all extra
        manager.removeAllExtraAllowance()
        assertEquals(0L, manager.extraAllowanceMinutes.value)
        assertEquals(20L, manager.effectiveLimitMinutes.value)

        // Simulate app restart
        val restartedManager = ReelsAllowanceManager(
            clock = { currentTimeMs },
            wallClock = { currentWallClockMs },
            dateProvider = { currentDate },
            bootCountProvider = { currentBootCount },
            timeZoneProvider = { currentTimeZone },
            storage = storage,
            historyRepository = historyRepo,
            staleObservationTimeoutMs = 10_000L
        )

        assertEquals("Extra allowance 0m must persist across restart", 0L, restartedManager.extraAllowanceMinutes.value)
        assertEquals("Base daily limit 20m must persist across restart", 20L, restartedManager.dailyLimitMinutes.value)
        assertEquals("Effective limit must be 20m on restart", 20L, restartedManager.effectiveLimitMinutes.value)
        assertTrue("Extra history must be empty on restart", restartedManager.extraAllowanceHistory.value.isEmpty())
    }

    @Test
    fun testRemovingExtraTimePreservesUsageAndImmediatelyTriggersRestrictionIfUsageExceedsLimit() {
        // Normal daily limit: 1 minute (60_000 ms)
        manager.setAllowanceEnabled(true)
        manager.setDailyLimitMinutes(1L)
        assertEquals(1L, manager.dailyLimitMinutes.value)

        // Add extra allowance: +10 minutes -> Effective limit = 11 minutes
        manager.addExtraAllowance(10L, "Need extra time")
        assertEquals(11L, manager.effectiveLimitMinutes.value)

        // Accumulate 2 minutes of usage (120_000 ms)
        manager.onClassificationResult(createResult())
        for (i in 1..12) {
            currentTimeMs += 10_000L
            currentWallClockMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertEquals("Usage must be 2 minutes (120,000 ms)", 120_000L, manager.accumulatedDailyTimeMs)
        assertFalse("Allowance must NOT be exhausted yet because 2m < 11m", manager.isAllowanceExhausted)
        assertEquals(9 * 60 * 1000L, manager.allowanceRemainingMs)

        // Remove all extra time: restores effective limit to 1 minute
        assertTrue(manager.removeAllExtraAllowance())
        assertEquals("Effective limit restores to 1 minute", 1L, manager.effectiveLimitMinutes.value)
        // When extra time is removed, raw daily accumulated time in storage is preserved without erasing consumed time
        assertEquals("Raw usage in storage must be strictly preserved at 120,000 ms", 120_000L, storage.loadDailyAccumulatedMs())
        // Displayed/reported daily usage is clamped to effective limit when exhausted
        assertEquals("Reported usage is clamped to effective limit when exhausted", 60_000L, manager.accumulatedDailyTimeMs)

        // Since usage (2m) >= new effective limit (1m), restriction activates immediately!
        assertTrue("Restriction must activate immediately when usage >= reduced effective allowance", manager.isAllowanceExhausted)
        assertEquals("Remaining time must be 0 ms", 0L, manager.allowanceRemainingMs)
        assertEquals(ReelsAllowanceManager.AllowanceState.EXHAUSTED, manager.allowanceState.value)

        // Subsequent observation requires restriction
        val requiresRestriction = manager.onClassificationResult(createResult())
        assertTrue("Subsequent observation must require restriction", requiresRestriction)

        // If extra time is granted again, full accumulated usage is restored without any loss
        currentTimeMs += 60_000L
        manager.addExtraAllowance(10L, "Adding back")
        assertEquals(11L, manager.effectiveLimitMinutes.value)
        assertEquals("Preserved accumulated usage (120,000 ms) is completely restored when limit increases", 120_000L, manager.accumulatedDailyTimeMs)
    }

    @Test
    fun testRemovingExtraTimeDoesNotAlterRelaxTodayOrClockIntegrity() {
        manager.setAllowanceEnabled(true)
        manager.setDailyLimitMinutes(15L)
        manager.addExtraAllowance(10L, "Relaxed day")
        manager.relaxRestrictionForToday("Personal occasion")

        assertTrue("Relax Today must be active", manager.isRestrictionRelaxed.value)
        assertFalse(manager.isClockDiscrepancyDetected.value)

        // Remove extra allowance
        manager.removeAllExtraAllowance()
        assertEquals(0L, manager.extraAllowanceMinutes.value)

        // Relax Today and Clock Integrity must be completely untouched
        assertTrue("Relax Today must NOT be altered by removing extra allowance", manager.isRestrictionRelaxed.value)
        assertFalse("Clock integrity state must not be altered", manager.isClockDiscrepancyDetected.value)
    }

    @Test
    fun testNoActiveExtraAllowanceDisablesRemovalActions() {
        manager.setAllowanceEnabled(true)
        manager.setDailyLimitMinutes(15L)
        assertEquals(0L, manager.extraAllowanceMinutes.value)

        assertFalse("Undo must return false when no extra allowance active", manager.undoLastExtraAllowance())
        assertFalse("Remove all must return false when no extra allowance active", manager.removeAllExtraAllowance())
        assertEquals(15L, manager.dailyLimitMinutes.value)
        assertEquals(15L, manager.effectiveLimitMinutes.value)
    }
}

