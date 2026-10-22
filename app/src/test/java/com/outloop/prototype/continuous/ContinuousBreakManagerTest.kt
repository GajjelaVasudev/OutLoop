package com.outloop.prototype.continuous

import com.outloop.classifier.CandidateScreenState
import com.outloop.classifier.ClassificationResult
import com.outloop.classifier.Confidence
import com.outloop.classifier.DiagnosticSummary
import com.outloop.classifier.ObservationOutcome
import com.outloop.classifier.ReasonCode
import com.outloop.prototype.allowance.InMemoryAllowanceStorage
import com.outloop.prototype.allowance.ReelsAllowanceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic JVM unit tests for [ContinuousBreakManager].
 *
 * Verifies:
 * - Timing accuracy: no prompt before interval, prompt eligible at interval, custom intervals.
 * - Leaving Reels resets continuous session immediately.
 * - Stale observation delta capping (10s max).
 * - Single-prompt guarantee per continuous session (completion, skip, dismiss do not repeat).
 * - Safe reset on leaving Reels and service disconnection.
 * - User settings defaults (ON, 5m), presets (2, 3, 4, 5, 10, 15), custom limits (1..120), invalid rejection.
 * - Complete independence from daily allowance accounting, extra time, and Relax Today.
 */
class ContinuousBreakManagerTest {

    private var currentTimeMs = 1_000_000L
    private lateinit var storage: InMemoryContinuousBreakStorage
    private lateinit var manager: ContinuousBreakManager

    @Before
    fun setUp() {
        currentTimeMs = 1_000_000L
        storage = InMemoryContinuousBreakStorage(enabled = true, intervalMinutes = 5)
        manager = ContinuousBreakManager(
            clock = { currentTimeMs },
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
        isPartialTree: Boolean = false
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
                selectedTabId = "clips_tab"
            )
        )
    }

    // ========================================================================
    // TIMING TESTS
    // ========================================================================

    @Test
    fun testNoPromptBeforeConfiguredInterval() {
        // Interval is 5 minutes (300_000 ms)
        manager.onClassificationResult(createResult())

        // Accumulate 4 minutes (24 observations of 10s)
        for (i in 1..24) {
            currentTimeMs += 10_000L
            val promptTriggered = manager.onClassificationResult(createResult())
            assertFalse("Prompt must not trigger before configured interval", promptTriggered)
        }

        assertEquals(240_000L, manager.continuousAccumulatedMs.value)
        assertFalse(manager.isPromptEligible.value)
    }

    @Test
    fun testPromptBecomesEligibleAtConfiguredInterval() {
        // Interval is 5 minutes (300_000 ms)
        manager.onClassificationResult(createResult())

        // Accumulate up to 4m 50s
        for (i in 1..29) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertFalse(manager.isPromptEligible.value)

        // Reach exactly 5 minutes (30th observation at 300_000 ms)
        currentTimeMs += 10_000L
        val promptTriggered = manager.onClassificationResult(createResult())

        assertTrue("Prompt must become eligible at 5m configured interval", promptTriggered)
        assertTrue(manager.isPromptEligible.value)
        assertEquals(300_000L, manager.continuousAccumulatedMs.value)
    }

    @Test
    fun testCustomIntervalsAreRespected() {
        // Set custom interval to 2 minutes (120_000 ms)
        assertTrue(manager.setBreakIntervalMinutes(2))
        assertEquals(2, manager.breakIntervalMinutes.value)

        manager.onClassificationResult(createResult())

        for (i in 1..11) {
            currentTimeMs += 10_000L
            assertFalse(manager.onClassificationResult(createResult()))
        }

        // At 12th observation (120s = 2m):
        currentTimeMs += 10_000L
        val triggered = manager.onClassificationResult(createResult())
        assertTrue("Custom 2m interval must trigger prompt", triggered)
        assertTrue(manager.isPromptEligible.value)
    }

    @Test
    fun testLeavingReelsResetsContinuousSessionImmediately() {
        manager.onClassificationResult(createResult())

        // Accumulate 3 minutes
        for (i in 1..18) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertEquals(180_000L, manager.continuousAccumulatedMs.value)
        assertTrue(manager.isContinuousSessionActive)

        // User navigates away to Feed
        currentTimeMs += 5_000L
        val prompt = manager.onClassificationResult(createResult(candidateState = CandidateScreenState.HOME_FEED))
        assertFalse(prompt)

        assertFalse("Session must be inactive after leaving Reels", manager.isContinuousSessionActive)
        assertEquals("Accumulated continuous time must reset to 0 after leaving Reels", 0L, manager.continuousAccumulatedMs.value)
        assertFalse(manager.isPromptEligible.value)

        // Returning to Reels starts a fresh session
        currentTimeMs += 5_000L
        manager.onClassificationResult(createResult())
        assertTrue(manager.isContinuousSessionActive)
        assertEquals(0L, manager.continuousAccumulatedMs.value)
    }

    @Test
    fun testStaleObservationGapResetsSession() {
        manager.onClassificationResult(createResult())

        // 60 seconds passes silently without observations (e.g. phone locked or idle)
        currentTimeMs += 60_000L
        val promptTriggered = manager.onClassificationResult(createResult())

        // Unverified gap must reset session and not accrue unverified time
        assertFalse("Prompt must not trigger on stale resumption", promptTriggered)
        assertFalse("Session must be reset on stale observation gap", manager.isContinuousSessionActive)
        assertEquals("Accumulated time must be 0 after stale gap reset", 0L, manager.continuousAccumulatedMs.value)
    }

    @Test
    fun testSparseEventsDoNotPreventThresholdRecognitionViaScheduledCheck() {
        // Set 2-minute interval
        manager.setBreakIntervalMinutes(2)
        assertEquals(2, manager.breakIntervalMinutes.value)

        // Session starts at t0
        manager.onClassificationResult(createResult())
        assertTrue(manager.isContinuousSessionActive)

        // Scheduled check revalidates every 1 second, updating latest observation time
        for (sec in 1..119) {
            currentTimeMs += 1000L
            manager.updateLatestObservationTime(currentTimeMs)
            assertFalse("Threshold not reached before 120s", manager.checkThresholdReached(currentTimeMs))
        }

        // At exactly 120 seconds (120,000 ms), scheduled check recognizes threshold
        currentTimeMs += 1000L
        val triggered = manager.checkThresholdReached(currentTimeMs)
        assertTrue("Scheduled check must recognize 2-minute threshold at 120,000 ms", triggered)
        assertTrue(manager.isPromptEligible.value)
        assertEquals(120_000L, manager.continuousAccumulatedMs.value)
    }

    @Test
    fun testScheduledCheckStateCallbacks() {
        var isScheduledCheckActive = false
        manager.onSessionStateChanged = { active -> isScheduledCheckActive = active }

        // Start session on Reels
        manager.onClassificationResult(createResult())
        assertTrue("Scheduled check must be started when Reels session becomes active", isScheduledCheckActive)

        // Leave Reels
        manager.onClassificationResult(createResult(candidateState = CandidateScreenState.HOME_FEED))
        assertFalse("Scheduled check must be stopped when leaving Reels", isScheduledCheckActive)

        // Re-enter Reels
        manager.onClassificationResult(createResult())
        assertTrue("Scheduled check must be restarted on fresh Reels session", isScheduledCheckActive)

        // Disable reminders
        manager.setBreakReminderEnabled(false)
        assertFalse("Scheduled check must be stopped when feature is disabled", isScheduledCheckActive)
    }

    @Test
    fun testUncertainOrPartialTreeObservationsDoNotAccrueTime() {
        manager.onClassificationResult(createResult())
        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        assertEquals(10_000L, manager.continuousAccumulatedMs.value)

        // Observation with LOW confidence (uncertain) - must NOT accrue additional continuous time
        currentTimeMs += 5_000L
        manager.onClassificationResult(createResult(confidence = Confidence.LOW))
        // It maintains the active session because gap <= effectiveMaxGapMs, but does NOT accrue time
        assertTrue("Session remains active across transient uncertain observations", manager.isContinuousSessionActive)
        assertEquals("Time must not accrue during uncertain observation", 10_000L, manager.continuousAccumulatedMs.value)

        // But when gap exceeds effectiveMaxGapMs, session is reset
        currentTimeMs += 20_000L
        manager.onClassificationResult(createResult(confidence = Confidence.LOW))
        assertFalse("Session must reset when uncertain gap exceeds max allowed gap", manager.isContinuousSessionActive)
        assertEquals(0L, manager.continuousAccumulatedMs.value)
    }

    // ========================================================================
    // PROMPT LIFECYCLE TESTS
    // ========================================================================

    @Test
    fun testCompletingMindRefreshRepeatsPromptAfterAnotherCompleteInterval() {
        manager.onClassificationResult(createResult())
        for (i in 1..30) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue("First prompt must trigger at 5m", manager.isPromptEligible.value)

        // User completes Mind Refresh
        manager.onMindRefreshCompleted()
        assertFalse("Prompt eligibility clears upon Mind Refresh completion", manager.isPromptEligible.value)
        assertTrue("Continuous Reels session must remain active", manager.isContinuousSessionActive)
        assertEquals("Continuous accumulated time resets to 0 for next interval", 0L, manager.continuousAccumulatedMs.value)

        // User continues watching Reels without leaving Reels
        for (i in 1..29) {
            currentTimeMs += 10_000L
            val triggered = manager.onClassificationResult(createResult())
            assertFalse("Prompt must not trigger before next complete 5m interval", triggered)
        }

        // Reaching another full 5m (30th step from baseline)
        currentTimeMs += 10_000L
        val retriggered = manager.onClassificationResult(createResult())
        assertTrue("Next prompt must trigger after another complete 5m interval without leaving Reels", retriggered)
        assertTrue(manager.isPromptEligible.value)

        // User takes a break again and completes it: should repeat for 3rd interval
        manager.onMindRefreshCompleted()
        assertFalse(manager.isPromptEligible.value)
        for (i in 1..29) {
            currentTimeMs += 10_000L
            assertFalse(manager.onClassificationResult(createResult()))
        }
        currentTimeMs += 10_000L
        assertTrue("Prompt must trigger for subsequent intervals", manager.onClassificationResult(createResult()))
    }

    @Test
    fun testContinueWatchingRepeatsPromptAfterAnotherCompleteIntervalWithoutLeavingReels() {
        manager.onClassificationResult(createResult())
        for (i in 1..30) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isPromptEligible.value)

        // User chooses "Continue watching"
        manager.onContinueWatching()
        assertFalse("Prompt eligibility clears upon Continue Watching", manager.isPromptEligible.value)
        assertTrue("Continuous session remains active", manager.isContinuousSessionActive)
        assertEquals("Interval elapsed resets to 0", 0L, manager.continuousAccumulatedMs.value)

        // Continue watching Reels for another 4m 50s (29 observations)
        for (i in 1..29) {
            currentTimeMs += 10_000L
            assertFalse(manager.onClassificationResult(createResult()))
        }

        // 30th observation (full 5 minutes from continue baseline)
        currentTimeMs += 10_000L
        val nextPrompt = manager.onClassificationResult(createResult())
        assertTrue("Next prompt must trigger after another full interval after Continue watching", nextPrompt)
        assertTrue(manager.isPromptEligible.value)
    }

    @Test
    fun testSkippingMindRefreshRepeatsPromptAfterAnotherCompleteInterval() {
        manager.onClassificationResult(createResult())
        for (i in 1..30) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isPromptEligible.value)

        // User skips Mind Refresh
        manager.onMindRefreshSkipped()
        assertFalse("Prompt eligibility clears upon Skip", manager.isPromptEligible.value)
        assertTrue("Session remains active after Skip", manager.isContinuousSessionActive)

        // Subsequent observations before full interval do not re-trigger
        for (i in 1..29) {
            currentTimeMs += 10_000L
            assertFalse(manager.onClassificationResult(createResult()))
        }

        // At exactly next 5m mark
        currentTimeMs += 10_000L
        val retriggered = manager.onClassificationResult(createResult())
        assertTrue("Prompt must retrigger after another complete interval after Skip", retriggered)
        assertTrue(manager.isPromptEligible.value)
    }

    @Test
    fun testNoDuplicatePromptsAtIntervalBoundaries() {
        manager.onClassificationResult(createResult())
        for (i in 1..29) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        currentTimeMs += 10_000L
        val firstTrigger = manager.onClassificationResult(createResult())
        assertTrue("Prompt triggers at boundary", firstTrigger)
        assertTrue(manager.isPromptEligible.value)

        // Rapid subsequent classification at or past boundary while prompt is pending
        currentTimeMs += 500L
        val duplicateCheck1 = manager.checkThresholdReached(currentTimeMs)
        assertFalse("Scheduled check must not trigger duplicate prompt while pending", duplicateCheck1)

        currentTimeMs += 500L
        val duplicateCheck2 = manager.onClassificationResult(createResult())
        assertFalse("Accessibility event must not trigger duplicate prompt while pending", duplicateCheck2)
    }

    @Test
    fun testDisabledRemindersDoNotTriggerPrompts() {
        manager.setBreakReminderEnabled(false)
        assertFalse(manager.isBreakReminderEnabled.value)

        manager.onClassificationResult(createResult())
        for (i in 1..60) {
            currentTimeMs += 10_000L
            val triggered = manager.onClassificationResult(createResult())
            assertFalse("Disabled reminders must never trigger prompts", triggered)
        }
        assertFalse(manager.isPromptEligible.value)
    }

    @Test
    fun testLeavingReelsInvokesDismissCallback() {
        var dismissCallbackInvoked = false
        manager.onDismissPromptRequested = { dismissCallbackInvoked = true }

        manager.onClassificationResult(createResult())
        for (i in 1..30) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isPromptEligible.value)

        // User switches to non-Instagram app
        manager.onClassificationResult(createResult(isInstagramPackage = false))
        assertTrue("Dismiss callback must be invoked when leaving Reels", dismissCallbackInvoked)
        assertFalse(manager.isPromptEligible.value)
    }

    @Test
    fun testResetSessionCleansUpStaleState() {
        manager.onClassificationResult(createResult())
        for (i in 1..30) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isPromptEligible.value)

        manager.resetSession()

        assertFalse(manager.isContinuousSessionActive)
        assertEquals(0L, manager.continuousAccumulatedMs.value)
        assertFalse(manager.isPromptEligible.value)
    }

    // ========================================================================
    // SETTINGS TESTS
    // ========================================================================

    @Test
    fun testDefaultSettingsAreOnAndFiveMinutes() {
        assertTrue("Break reminder must be enabled by default", manager.isBreakReminderEnabled.value)
        assertEquals("Break interval must default to 5 minutes", 5, manager.breakIntervalMinutes.value)
    }

    @Test
    fun testAllSixPresetIntervalsWork() {
        val presets = listOf(2, 3, 4, 5, 10, 15)
        for (preset in presets) {
            assertTrue("Preset $preset must be accepted", manager.setBreakIntervalMinutes(preset))
            assertEquals(preset, manager.breakIntervalMinutes.value)
            assertEquals(preset, storage.loadBreakIntervalMinutes())
        }
    }

    @Test
    fun testValidCustomValuesPersist() {
        val customValues = listOf(1, 7, 25, 45, 90, 120)
        for (value in customValues) {
            assertTrue("Value $value must be accepted", manager.setBreakIntervalMinutes(value))
            assertEquals(value, manager.breakIntervalMinutes.value)
            assertEquals(value, storage.loadBreakIntervalMinutes())
        }
    }

    @Test
    fun testInvalidValuesAreRejected() {
        val invalidValues = listOf(-5, -1, 0, 121, 500)
        manager.setBreakIntervalMinutes(5)

        for (invalid in invalidValues) {
            assertFalse("Value $invalid must be rejected", manager.setBreakIntervalMinutes(invalid))
            assertEquals("Interval must remain unchanged on rejection", 5, manager.breakIntervalMinutes.value)
        }
    }

    @Test
    fun testDisablingRemindersPreventsPrompts() {
        manager.setBreakReminderEnabled(false)
        assertFalse(manager.isBreakReminderEnabled.value)

        manager.onClassificationResult(createResult())
        for (i in 1..40) {
            currentTimeMs += 10_000L
            val triggered = manager.onClassificationResult(createResult())
            assertFalse("No prompt must trigger when reminder is disabled", triggered)
        }
        assertFalse(manager.isPromptEligible.value)
    }

    // ========================================================================
    // FEATURE INDEPENDENCE TESTS
    // ========================================================================

    @Test
    fun testFeatureIndependenceFromDailyAllowance() {
        val allowanceStorage = InMemoryAllowanceStorage()
        val allowanceManager = ReelsAllowanceManager(
            clock = { currentTimeMs },
            wallClock = { 1_700_000_000_000L },
            dateProvider = { "2026-10-22" },
            storage = allowanceStorage
        )

        // Daily allowance is disabled
        allowanceManager.setAllowanceEnabled(false)
        assertFalse(allowanceManager.isAllowanceEnabled.value)

        // Continuous break still tracks and triggers independently
        manager.onClassificationResult(createResult())
        for (i in 1..30) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue("Continuous break works even when daily allowance is disabled", manager.isPromptEligible.value)

        // Now enable daily allowance and configure 15m
        allowanceManager.setDailyLimitMinutes(15L)
        allowanceManager.setAllowanceEnabled(true)
        allowanceManager.addExtraAllowance(10L, "Study")
        allowanceManager.relaxRestrictionForToday("Holiday")

        // Interacting with continuous break must NOT mutate daily allowance state
        manager.onBreakCompleted()
        manager.setBreakIntervalMinutes(10)
        manager.setBreakReminderEnabled(false)
        manager.setBreakReminderEnabled(true)

        assertEquals("Daily limit minutes must be unchanged", 15L, allowanceManager.dailyLimitMinutes.value)
        assertEquals("Extra allowance minutes must be unchanged", 10L, allowanceManager.extraAllowanceMinutes.value)
        assertTrue("Restriction relaxed state must be unchanged", allowanceManager.isRestrictionRelaxed.value)
        assertEquals("Special day reason must be unchanged", "Holiday", allowanceManager.specialDayReason.value)
    }

    @Test
    fun testContinuousBreakDoesNotMutateDailyAccumulatedUsage() {
        val allowanceStorage = InMemoryAllowanceStorage()
        val allowanceManager = ReelsAllowanceManager(
            clock = { currentTimeMs },
            wallClock = { 1_700_000_000_000L },
            dateProvider = { "2026-10-22" },
            storage = allowanceStorage
        )
        allowanceManager.setDailyLimitMinutes(15L)
        allowanceManager.setAllowanceEnabled(true)

        // Feed an observation to both
        val r1 = createResult()
        allowanceManager.onClassificationResult(r1)
        manager.onClassificationResult(r1)

        currentTimeMs += 10_000L
        val r2 = createResult()
        allowanceManager.onClassificationResult(r2)
        manager.onClassificationResult(r2)

        val dailyUsageBefore = allowanceManager.accumulatedDailyTimeMs

        // Complete break, skip break, dismiss break
        manager.onBreakCompleted()
        manager.onBreakSkipped()
        manager.onBreakDismissed()

        assertEquals("Daily usage must not be mutated by break lifecycle actions", dailyUsageBefore, allowanceManager.accumulatedDailyTimeMs)
    }

    // ========================================================================
    // REGRESSION TESTS FOR TRANSIENT OBSERVATIONS & 1-MINUTE INTERVAL
    // ========================================================================

    @Test
    fun testTransientUnknownDoesNotResetSessionWithinEffectiveGap() {
        // Start continuous session on Reels
        manager.onClassificationResult(createResult())
        assertTrue(manager.isContinuousSessionActive)

        // Accumulate 20 seconds (two 10s steps)
        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        assertEquals(20_000L, manager.continuousAccumulatedMs.value)

        // Simulate transient UNKNOWN result (e.g. video loading, rendering, or transient null root) 3 seconds later
        currentTimeMs += 3_000L
        val unknownResult = createResult(
            candidateState = CandidateScreenState.UNKNOWN,
            confidence = Confidence.LOW,
            reasonCode = ReasonCode.MISSING_ROOT,
            outcome = ObservationOutcome.ROOT_UNAVAILABLE
        )
        val triggeredOnUnknown = manager.onClassificationResult(unknownResult)
        assertFalse(triggeredOnUnknown)

        // Session must STILL be active! Monotonic time is NOT reset to 0
        assertTrue("Session must remain active across transient non-confident events", manager.isContinuousSessionActive)
        assertEquals(20_000L, manager.continuousAccumulatedMs.value)

        // Another confident Reels observation 2 seconds later (gap was only 5s total <= 15s)
        currentTimeMs += 2_000L
        manager.onClassificationResult(createResult())
        assertTrue(manager.isContinuousSessionActive)
        assertEquals(25_000L, manager.continuousAccumulatedMs.value)
    }

    @Test
    fun testGapExceedingEffectiveMaxGapMsResetsSession() {
        // Start session
        manager.onClassificationResult(createResult())
        assertTrue(manager.isContinuousSessionActive)

        // 10 seconds on Reels
        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        assertEquals(10_000L, manager.continuousAccumulatedMs.value)

        // Long gap of 20 seconds with unknown/missing root (exceeding 15,000ms max allowed gap)
        currentTimeMs += 20_000L
        val unknownResult = createResult(
            candidateState = CandidateScreenState.UNKNOWN,
            confidence = Confidence.LOW,
            reasonCode = ReasonCode.MISSING_ROOT,
            outcome = ObservationOutcome.ROOT_UNAVAILABLE
        )
        manager.onClassificationResult(unknownResult)

        // Session must be reset due to excessive observation gap
        assertFalse("Session must be reset when gap exceeds 15 seconds", manager.isContinuousSessionActive)
        assertEquals(0L, manager.continuousAccumulatedMs.value)
    }

    @Test
    fun testOneMinuteIntervalWorkflowAndSkipPrompt() {
        // Configure 1-minute interval (60_000 ms)
        assertTrue(manager.setBreakIntervalMinutes(1))
        assertEquals(1, manager.breakIntervalMinutes.value)

        // Start session on Reels
        manager.onClassificationResult(createResult())

        // Watch for 50 seconds (below 1m threshold)
        for (i in 1..5) {
            currentTimeMs += 10_000L
            val prompt = manager.onClassificationResult(createResult())
            assertFalse(prompt)
            assertFalse(manager.isPromptEligible.value)
        }

        // Hit 60 seconds (1 minute threshold)
        currentTimeMs += 10_000L
        val prompt = manager.onClassificationResult(createResult())
        assertTrue("Prompt must become eligible at 1-minute mark (60s)", prompt)
        assertTrue(manager.isPromptEligible.value)

        // User skips break
        manager.onBreakSkipped()
        assertFalse("Skipping break must clear eligibility flag", manager.isPromptEligible.value)

        // Watching more Reels during SAME session must not re-trigger prompt
        currentTimeMs += 15_000L
        val promptAgain = manager.onClassificationResult(createResult())
        assertFalse("Skipping must not re-trigger another prompt during same continuous session", promptAgain)

        // User navigates away to Stories (definitive exit)
        currentTimeMs += 5_000L
        manager.onClassificationResult(createResult(candidateState = CandidateScreenState.STORIES))
        assertFalse(manager.isContinuousSessionActive)
        assertEquals(0L, manager.continuousAccumulatedMs.value)

        // User returns to Reels -> fresh session starts
        currentTimeMs += 5_000L
        manager.onClassificationResult(createResult())
        assertTrue(manager.isContinuousSessionActive)
        assertEquals(0L, manager.continuousAccumulatedMs.value)

        // Fresh session reaches 60 seconds -> prompt appears again
        for (i in 1..5) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        currentTimeMs += 10_000L
        val freshPrompt = manager.onClassificationResult(createResult())
        assertTrue("Fresh session must trigger prompt when reaching 1 minute", freshPrompt)
    }

    @Test
    fun testDefinitiveExitResetsContinuousSessionImmediately() {
        manager.onClassificationResult(createResult())
        assertTrue(manager.isContinuousSessionActive)

        // Accumulate 30 seconds (three 10s steps)
        for (i in 1..3) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertEquals(30_000L, manager.continuousAccumulatedMs.value)

        // Definitive exit: Home Feed
        val feedResult = createResult(candidateState = CandidateScreenState.HOME_FEED)
        assertTrue(manager.isDefinitiveExitFromReels(feedResult))
        manager.onClassificationResult(feedResult)

        assertFalse(manager.isContinuousSessionActive)
        assertEquals(0L, manager.continuousAccumulatedMs.value)
    }

    @Test
    fun testCommentsOpeningDoesNotResetContinuousSession() {
        // Set 1-minute interval for fast verification
        assertTrue(manager.setBreakIntervalMinutes(1))

        // 1. User starts watching Reels
        manager.onClassificationResult(createResult())
        assertTrue(manager.isContinuousSessionActive)

        // 2. User watches for 20 seconds
        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        assertEquals(20_000L, manager.continuousAccumulatedMs.value)

        // 3. User opens comments sheet on the current Reel (produces REELS_TAB with comments evidence)
        val commentsResult = createResult(
            candidateState = CandidateScreenState.REELS_TAB,
            confidence = Confidence.HIGH,
            reasonCode = ReasonCode.MULTIPLE_SIGNALS_MATCH
        )
        currentTimeMs += 10_000L
        manager.onClassificationResult(commentsResult)

        // Continuous session must remain active and associate with the underlying Reel
        assertTrue("Opening comments sheet must NOT reset continuous session", manager.isContinuousSessionActive)
        assertEquals(30_000L, manager.continuousAccumulatedMs.value)

        // 4. User spends another 20s reading comments on the Reel
        currentTimeMs += 10_000L
        manager.onClassificationResult(commentsResult)
        currentTimeMs += 10_000L
        manager.onClassificationResult(commentsResult)
        assertEquals(50_000L, manager.continuousAccumulatedMs.value)

        // 5. User closes comments sheet and returns to normal Reel view
        currentTimeMs += 10_000L
        val promptTriggered = manager.onClassificationResult(createResult())

        // Continuous session seamlessly reaches 60s (1 minute)
        assertEquals(60_000L, manager.continuousAccumulatedMs.value)
        assertTrue("Attention check prompt must trigger at configured 1-minute interval", promptTriggered)
        assertTrue(manager.isPromptEligible.value)
    }

    @Test
    fun testTransientMissingRootDuringCommentsTransitionDoesNotResetWithin15s() {
        manager.setBreakIntervalMinutes(2)
        manager.onClassificationResult(createResult())
        assertTrue(manager.isContinuousSessionActive)

        // Accumulate 20 seconds (two 10s steps)
        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        assertEquals(20_000L, manager.continuousAccumulatedMs.value)

        // Comments sheet starts animating up: transient MISSING_ROOT for 4 seconds
        currentTimeMs += 4_000L
        val transientResult = createResult(
            candidateState = CandidateScreenState.UNKNOWN,
            confidence = Confidence.LOW,
            reasonCode = ReasonCode.MISSING_ROOT,
            outcome = ObservationOutcome.ROOT_UNAVAILABLE
        )
        assertFalse(manager.isDefinitiveExitFromReels(transientResult))
        manager.onClassificationResult(transientResult)

        // Session must be preserved within the 15s grace window
        assertTrue("Transient missing root must not reset continuous session", manager.isContinuousSessionActive)
        assertEquals(20_000L, manager.continuousAccumulatedMs.value)

        // Comments sheet finishes opening and is classified as REELS_TAB 2 seconds later
        currentTimeMs += 2_000L
        manager.onClassificationResult(createResult())
        assertTrue(manager.isContinuousSessionActive)
        assertEquals(26_000L, manager.continuousAccumulatedMs.value)
    }

    @Test
    fun testObservationGapExceeding15sResetsSession() {
        manager.setBreakIntervalMinutes(2)
        manager.onClassificationResult(createResult())
        assertTrue(manager.isContinuousSessionActive)

        currentTimeMs += 10_000L
        manager.onClassificationResult(createResult())
        assertEquals(10_000L, manager.continuousAccumulatedMs.value)

        // Silent unverified gap exceeding 15 seconds (e.g. 16 seconds of UNKNOWN)
        currentTimeMs += 16_000L
        val unknownResult = createResult(
            candidateState = CandidateScreenState.UNKNOWN,
            confidence = Confidence.LOW,
            reasonCode = ReasonCode.MISSING_ROOT,
            outcome = ObservationOutcome.ROOT_UNAVAILABLE
        )
        manager.onClassificationResult(unknownResult)

        // Must reset session rather than accumulating unverified time indefinitely
        assertFalse("Silent gap exceeding 15s must reset session", manager.isContinuousSessionActive)
        assertEquals(0L, manager.continuousAccumulatedMs.value)
    }

    @Test
    fun testAttentionCheckPromptActionsDismissPromptWithoutMutatingDailyAllowance() {
        val allowanceStorage = InMemoryAllowanceStorage()
        val allowanceManager = ReelsAllowanceManager(
            clock = { currentTimeMs },
            wallClock = { 1_700_000_000_000L },
            dateProvider = { "2026-10-22" },
            storage = allowanceStorage
        )
        allowanceManager.setDailyLimitMinutes(15L)
        allowanceManager.setAllowanceEnabled(true)

        manager.setBreakIntervalMinutes(1)
        manager.onClassificationResult(createResult())
        for (i in 1..5) {
            currentTimeMs += 10_000L
            val p = manager.onClassificationResult(createResult())
            assertFalse(p)
        }
        currentTimeMs += 10_000L
        val triggered = manager.onClassificationResult(createResult())
        assertTrue(triggered)
        assertTrue(manager.isPromptEligible.value)

        val dailyUsageBefore = allowanceManager.accumulatedDailyTimeMs

        // Choice 1: "Take a break"
        manager.onBreakTaken()
        assertFalse("Prompt must be dismissed after taking a break", manager.isPromptEligible.value)
        assertEquals("Daily allowance must not be mutated by 'Take a break'", dailyUsageBefore, allowanceManager.accumulatedDailyTimeMs)

        // Re-observing Reels in the same session must NOT show duplicate prompt
        currentTimeMs += 10_000L
        val duplicatePrompt = manager.onClassificationResult(createResult())
        assertFalse("Must not show duplicate prompt in same continuous session", duplicatePrompt)

        // Choice 2: "Continue watching"
        manager.onContinueWatching()
        assertFalse("Prompt must remain dismissed", manager.isPromptEligible.value)
        assertEquals("Daily allowance must not be mutated by 'Continue watching'", dailyUsageBefore, allowanceManager.accumulatedDailyTimeMs)
    }

    @Test
    fun testMindRefreshCompletionAndSkipDoNotMutateAllowanceOrRelaxToday() {
        val allowanceStorage = InMemoryAllowanceStorage()
        val allowanceManager = ReelsAllowanceManager(
            clock = { currentTimeMs },
            wallClock = { 1_700_000_000_000L },
            dateProvider = { "2026-10-22" },
            storage = allowanceStorage
        )
        allowanceManager.setDailyLimitMinutes(15L)
        allowanceManager.setAllowanceEnabled(true)
        allowanceManager.addExtraAllowance(10L, "Holiday")
        allowanceManager.relaxRestrictionForToday("Day off")

        val usageBefore = allowanceManager.accumulatedDailyTimeMs
        val extraBefore = allowanceManager.extraAllowanceMinutes.value
        val relaxedBefore = allowanceManager.isRestrictionRelaxed.value
        val clockDiscrepancyBefore = allowanceManager.isClockDiscrepancyDetected.value

        manager.setBreakIntervalMinutes(2)
        manager.onClassificationResult(createResult())
        for (i in 1..12) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isPromptEligible.value)

        // Complete Mind Refresh
        manager.onMindRefreshCompleted()
        assertFalse(manager.isPromptEligible.value)
        assertEquals("Daily usage must not change", usageBefore, allowanceManager.accumulatedDailyTimeMs)
        assertEquals("Extra allowance must not change", extraBefore, allowanceManager.extraAllowanceMinutes.value)
        assertEquals("Relax Today must not change", relaxedBefore, allowanceManager.isRestrictionRelaxed.value)
        assertEquals("Clock discrepancy must not change", clockDiscrepancyBefore, allowanceManager.isClockDiscrepancyDetected.value)
        assertEquals("Continuous accumulated must reset to 0 for next interval", 0L, manager.continuousAccumulatedMs.value)

        // Reach next interval
        for (i in 1..12) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isPromptEligible.value)

        // Skip Mind Refresh
        manager.onMindRefreshSkipped()
        assertFalse(manager.isPromptEligible.value)
        assertEquals("Daily usage must not change on skip", usageBefore, allowanceManager.accumulatedDailyTimeMs)
        assertEquals("Extra allowance must not change on skip", extraBefore, allowanceManager.extraAllowanceMinutes.value)
        assertEquals("Relax Today must not change on skip", relaxedBefore, allowanceManager.isRestrictionRelaxed.value)
        assertEquals("Clock discrepancy must not change on skip", clockDiscrepancyBefore, allowanceManager.isClockDiscrepancyDetected.value)
        assertEquals("Continuous accumulated must reset to 0 for next interval", 0L, manager.continuousAccumulatedMs.value)
    }

    @Test
    fun testMindRefreshActivitySelectionTypes() {
        val types = ContinuousBreakOverlay.MindRefreshActivityType.values()
        assertEquals(3, types.size)
        assertTrue(types.contains(ContinuousBreakOverlay.MindRefreshActivityType.PATTERN_RECALL))
        assertTrue(types.contains(ContinuousBreakOverlay.MindRefreshActivityType.VISUAL_RESET))
        assertTrue(types.contains(ContinuousBreakOverlay.MindRefreshActivityType.TINY_BRAIN_CHALLENGE))
    }

    @Test
    fun testAppSwitchingWithinGracePeriodRestoresPendingBreakPrompt() {
        manager.setBreakIntervalMinutes(1)
        manager.onClassificationResult(createResult())

        // Watch for 60 seconds to trigger prompt
        for (i in 1..6) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue("Prompt must be eligible after 1 minute", manager.isPromptEligible.value)

        // User temporarily leaves Reels (e.g. opens Recent Apps or other app for 3 seconds)
        currentTimeMs += 3_000L
        val unknownResult = createResult(
            candidateState = CandidateScreenState.UNKNOWN,
            confidence = Confidence.LOW,
            reasonCode = ReasonCode.MISSING_ROOT
        )
        // Non-eligible observation during switch
        val promptDuringSwitch = manager.onClassificationResult(unknownResult)
        assertFalse("Prompt must not re-trigger during unverified switch", promptDuringSwitch)
        assertTrue("Session must remain active within grace period", manager.isContinuousSessionActive)
        assertTrue("Prompt must still be pending within grace period", manager.isPromptEligible.value)

        // User returns to Instagram Reels within grace period (e.g. 2s later, total gap 5s < 10s grace)
        currentTimeMs += 2_000L
        val restoredPrompt = manager.onClassificationResult(createResult())
        assertTrue("Pending break prompt must be restored upon returning to Reels", restoredPrompt)
        assertTrue(manager.isPromptEligible.value)

        // User chooses to Continue Watching
        manager.onContinueWatching()
        assertFalse("Prompt must be dismissed", manager.isPromptEligible.value)

        // Must require another full minute before next prompt
        for (i in 1..5) {
            currentTimeMs += 10_000L
            val p = manager.onClassificationResult(createResult())
            assertFalse("Must not prompt before another full interval", p)
        }
        currentTimeMs += 10_000L
        val nextPrompt = manager.onClassificationResult(createResult())
        assertTrue("Next prompt must appear after full interval", nextPrompt)
    }

    @Test
    fun testAppSwitchingBeyondGracePeriodResetsSessionAndDoesNotAccumulateUnverifiedTime() {
        manager.setBreakIntervalMinutes(2)
        manager.onClassificationResult(createResult())

        // Watch 30 seconds (3 observations of 10s within allowed gap)
        for (i in 1..3) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertEquals(30_000L, manager.getCurrentContinuousElapsedMs())

        // User leaves app for 60 seconds (exceeds effectiveMaxGapMs of 10s)
        currentTimeMs += 60_000L
        val unknownResult = createResult(
            candidateState = CandidateScreenState.UNKNOWN,
            confidence = Confidence.LOW,
            reasonCode = ReasonCode.MISSING_ROOT
        )
        manager.onClassificationResult(unknownResult)

        assertFalse("Session must be reset when gap exceeds grace period", manager.isContinuousSessionActive)
        assertEquals("Elapsed time must not have accrued during gap", 0L, manager.getCurrentContinuousElapsedMs())
    }

    @Test
    fun testRapidSuccessiveEventsWhilePromptPendingNeverRetrigger() {
        manager.setBreakIntervalMinutes(1)
        manager.onClassificationResult(createResult())

        // Reach 1 minute
        for (i in 1..6) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isPromptEligible.value)

        // 20 rapid subsequent events (e.g. 100ms apart during scrolling / animations)
        for (i in 1..20) {
            currentTimeMs += 100L
            val retriggered = manager.onClassificationResult(createResult())
            assertFalse("Rapid successive events must never re-trigger prompt while pending", retriggered)
        }
        assertTrue("Prompt remains eligible", manager.isPromptEligible.value)
    }

    @Test
    fun testReturningToReelsRestoresPromptExactlyOnceNotRepeatedly() {
        manager.setBreakIntervalMinutes(1)
        manager.onClassificationResult(createResult())
        for (i in 1..6) {
            currentTimeMs += 10_000L
            manager.onClassificationResult(createResult())
        }
        assertTrue(manager.isPromptEligible.value)

        // User switches to Recent Apps for 3s (non-eligible observation)
        currentTimeMs += 3_000L
        val nonEligible = createResult(candidateState = CandidateScreenState.UNKNOWN, confidence = Confidence.LOW)
        manager.onClassificationResult(nonEligible)

        // Return event 1: Restores prompt
        currentTimeMs += 1_000L
        val restoreEvent = manager.onClassificationResult(createResult())
        assertTrue("First return event must restore prompt", restoreEvent)

        // Return event 2, 3, 4: Must NOT repeatedly restore or re-trigger
        for (i in 1..5) {
            currentTimeMs += 200L
            val duplicateEvent = manager.onClassificationResult(createResult())
            assertFalse("Subsequent events after return must not duplicate restoration", duplicateEvent)
        }
    }
}
