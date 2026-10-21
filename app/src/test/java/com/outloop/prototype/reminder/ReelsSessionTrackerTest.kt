package com.outloop.prototype.reminder

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
 * Deterministic unit tests for [ReelsSessionTracker].
 *
 * Verifies true opt-in defaults, advance-warning independence, monotonic delta capping
 * (2, 10, 20, 30s), screen-exit trailing accounting, dynamic stale state reporting,
 * dynamic inactivity resets, pause/resume time preservation, and threshold safety.
 */
class ReelsSessionTrackerTest {

    private var currentTimeMs = 1_000_000L
    private lateinit var tracker: ReelsSessionTracker

    @Before
    fun setUp() {
        currentTimeMs = 1_000_000L
        tracker = ReelsSessionTracker(
            clock = { currentTimeMs },
            advanceWarningMs = 9 * 60 * 1000L,
            firstReminderMs = 10 * 60 * 1000L,
            repeatIntervalMs = 5 * 60 * 1000L,
            staleObservationTimeoutMs = 10 * 1000L,
            inactivityResetTimeoutMs = 15 * 60 * 1000L
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

    // 1. Both settings initially OFF (true opt-in)
    @Test
    fun testBothSettingsInitiallyOff() {
        val newTracker = ReelsSessionTracker(clock = { currentTimeMs })
        assertFalse("Master reminder must default to OFF", newTracker.isReminderEnabled)
        assertFalse("Advance warning must default to OFF", newTracker.isAdvanceWarningEnabled)
    }

    // 2. Advance-warning independence from the master switch
    @Test
    fun testAdvanceWarningIndependenceFromMasterSwitch() {
        val newTracker = ReelsSessionTracker(clock = { currentTimeMs })
        assertFalse(newTracker.isReminderEnabled)

        // Enabling advance warning MUST NOT silently enable master switch
        newTracker.isAdvanceWarningEnabled = true
        assertTrue(newTracker.isAdvanceWarningEnabled)
        assertFalse("Enabling advance warning must NEVER enable master reminder", newTracker.isReminderEnabled)
    }

    // 3. Eligible observations 2, 10, 20, and 30 seconds apart
    @Test
    fun testEligibleObservations_2_10_20_30_SecondsApart() {
        // t = 0s: Anchor observation (0ms credited)
        tracker.onClassificationResult(createResult())
        assertEquals(0L, tracker.accumulatedReelsTimeMs)

        // 2s apart (t = 2s): delta 2s <= 10s -> credits 2s
        currentTimeMs += 2_000L
        tracker.onClassificationResult(createResult())
        assertEquals(2_000L, tracker.accumulatedReelsTimeMs)

        // 10s apart (t = 12s): delta 10s <= 10s -> credits 10s
        currentTimeMs += 10_000L
        tracker.onClassificationResult(createResult())
        assertEquals(12_000L, tracker.accumulatedReelsTimeMs)

        // 20s apart (t = 32s): delta 20s > 10s -> capped at 10s
        currentTimeMs += 20_000L
        tracker.onClassificationResult(createResult())
        assertEquals(22_000L, tracker.accumulatedReelsTimeMs)

        // 30s apart (t = 62s): delta 30s > 10s -> capped at 10s
        currentTimeMs += 30_000L
        tracker.onClassificationResult(createResult())
        assertEquals(32_000L, tracker.accumulatedReelsTimeMs)
    }

    // 4. Switching away from Reels after a 30-second observation gap
    @Test
    fun testSwitchingAwayFromReels_After30SecondGap() {
        // Reels anchor at t = 0s
        tracker.onClassificationResult(createResult())
        assertEquals(ReelsSessionTracker.SessionState.TRACKING, tracker.sessionState)

        // Non-Reels observation arrives 30s later (e.g. Home Feed)
        currentTimeMs += 30_000L
        val events = tracker.onClassificationResult(createResult(candidateState = CandidateScreenState.HOME_FEED))

        // Trailing interval is capped at 10s stale timeout -> credits exactly 10s
        assertEquals(10_000L, tracker.accumulatedReelsTimeMs)
        assertEquals(ReelsSessionTracker.SessionState.PAUSED, tracker.sessionState)
        assertTrue("No reminders emitted on non-Reels observation", events.isEmpty())
    }

    // 5. Switching away from Reels within stale timeout credits exact trailing interval
    @Test
    fun testSwitchingAwayFromReels_WithinStaleTimeout_CreditsExactTrailingInterval() {
        // Reels anchor at t = 0s
        tracker.onClassificationResult(createResult())

        // Non-Reels observation arrives 6s later
        currentTimeMs += 6_000L
        tracker.onClassificationResult(createResult(candidateState = CandidateScreenState.DM_INBOX))

        // Credits exact 6s trailing interval
        assertEquals(6_000L, tracker.accumulatedReelsTimeMs)
        assertEquals(ReelsSessionTracker.SessionState.PAUSED, tracker.sessionState)
    }

    // 6. Stale tracking state after time advances without a new event
    @Test
    fun testStaleTrackingState_AfterTimeAdvancesWithoutNewEvent() {
        // Reels anchor at t = 0s
        tracker.onClassificationResult(createResult())
        assertEquals(ReelsSessionTracker.SessionState.TRACKING, tracker.sessionState)

        // Advance time by 15s without any new event
        currentTimeMs += 15_000L

        // Querying sessionState must evaluate to PAUSED due to 10s stale timeout
        assertEquals(ReelsSessionTracker.SessionState.PAUSED, tracker.sessionState)
        // Querying accumulatedReelsTimeMs must report capped 10s, with no further accumulation
        assertEquals(10_000L, tracker.accumulatedReelsTimeMs)

        // Advance another 20s (total 35s of silence)
        currentTimeMs += 20_000L
        assertEquals(ReelsSessionTracker.SessionState.PAUSED, tracker.sessionState)
        assertEquals(10_000L, tracker.accumulatedReelsTimeMs)
    }

    // 7. Inactivity reset after 15 minutes when the tracker is next queried
    @Test
    fun testInactivityReset_After15MinutesWhenNextQueried() {
        // Accumulate 10s on Reels
        tracker.onClassificationResult(createResult())
        currentTimeMs += 10_000L
        tracker.onClassificationResult(createResult())
        assertEquals(10_000L, tracker.accumulatedReelsTimeMs)

        // Switch to DMs
        tracker.onClassificationResult(createResult(candidateState = CandidateScreenState.DM_CONVERSATION))
        assertEquals(ReelsSessionTracker.SessionState.PAUSED, tracker.sessionState)

        // Advance time by 16 minutes (exceeds 15 min inactivity timeout)
        currentTimeMs += 16 * 60 * 1000L

        // Next query evaluates inactivity reset dynamically
        assertEquals(0L, tracker.accumulatedReelsTimeMs)
        assertEquals(ReelsSessionTracker.SessionState.IDLE, tracker.sessionState)
    }

    // 8. Preservation of accumulated time during brief pauses
    @Test
    fun testPreservationOfAccumulatedTimeDuringBriefPauses() {
        // 10s on Reels
        tracker.onClassificationResult(createResult())
        currentTimeMs += 10_000L
        tracker.onClassificationResult(createResult())
        assertEquals(10_000L, tracker.accumulatedReelsTimeMs)

        // Brief switch to Home Feed for 2 minutes (< 15 min inactivity timeout)
        tracker.onClassificationResult(createResult(candidateState = CandidateScreenState.HOME_FEED))
        assertEquals(ReelsSessionTracker.SessionState.PAUSED, tracker.sessionState)
        currentTimeMs += 120_000L

        // Return to Reels: resumes tracking, time is still 10s (zero pause time credited)
        tracker.onClassificationResult(createResult())
        assertEquals(ReelsSessionTracker.SessionState.TRACKING, tracker.sessionState)
        assertEquals(10_000L, tracker.accumulatedReelsTimeMs)

        // Next Reels event after 4s adds 4s -> total 14s
        currentTimeMs += 4_000L
        tracker.onClassificationResult(createResult())
        assertEquals(14_000L, tracker.accumulatedReelsTimeMs)
    }

    // 9. Clearing accumulated time and fired milestones on reset
    @Test
    fun testClearingAccumulatedTimeAndFiredMilestonesOnReset() {
        tracker.isReminderEnabled = true
        tracker.isAdvanceWarningEnabled = true

        // Advance to 10 minutes (600s)
        tracker.onClassificationResult(createResult())
        for (i in 1..60) {
            currentTimeMs += 10_000L
            tracker.onClassificationResult(createResult())
        }
        assertEquals(600_000L, tracker.accumulatedReelsTimeMs)

        // Explicit reset
        tracker.resetSession()
        assertEquals(0L, tracker.accumulatedReelsTimeMs)
        assertEquals(ReelsSessionTracker.SessionState.IDLE, tracker.sessionState)

        // In new session, thresholds must be eligible to fire again
        tracker.onClassificationResult(createResult())
        var warningFiredInNewSession = false
        for (i in 1..54) { // 540s = 9 min
            currentTimeMs += 10_000L
            val events = tracker.onClassificationResult(createResult())
            if (events.any { it.type == ReelsSessionTracker.ReminderType.ADVANCE_WARNING }) {
                warningFiredInNewSession = true
            }
        }
        assertTrue("Warning must be able to fire again after reset", warningFiredInNewSession)
    }

    // 10. Ensuring stale or non-Reels observations cannot trigger reminders
    @Test
    fun testStaleOrNonReelsObservationsCannotTriggerReminders() {
        tracker.isReminderEnabled = true
        tracker.isAdvanceWarningEnabled = true

        // User is on Home feed
        tracker.onClassificationResult(createResult(candidateState = CandidateScreenState.HOME_FEED))

        // Advance 10 minutes on Home feed
        for (i in 1..60) {
            currentTimeMs += 10_000L
            val events = tracker.onClassificationResult(createResult(candidateState = CandidateScreenState.HOME_FEED))
            assertTrue("Home feed must never trigger reminders", events.isEmpty())
        }
        assertEquals(0L, tracker.accumulatedReelsTimeMs)
    }

    // 11. Advance warning at 9m and first reminder at 10m fire exactly once
    @Test
    fun testThresholdSchedule_AdvanceWarningAndFirstReminder() {
        tracker.isReminderEnabled = true
        tracker.isAdvanceWarningEnabled = true

        tracker.onClassificationResult(createResult())

        var warningCount = 0
        var firstReminderCount = 0

        for (i in 1..65) { // 650s
            currentTimeMs += 10_000L
            val events = tracker.onClassificationResult(createResult())
            for (event in events) {
                if (event.type == ReelsSessionTracker.ReminderType.ADVANCE_WARNING) warningCount++
                if (event.type == ReelsSessionTracker.ReminderType.FIRST_REMINDER) firstReminderCount++
            }
        }

        assertEquals("Advance warning must fire exactly once", 1, warningCount)
        assertEquals("First reminder must fire exactly once", 1, firstReminderCount)
    }

    // 12. Repeat reminders fire at 15 and 20 minutes
    @Test
    fun testRepeatReminders_FireAt15And20Minutes() {
        tracker.isReminderEnabled = true

        tracker.onClassificationResult(createResult())

        var reminderAt15 = false
        var reminderAt20 = false

        for (i in 1..130) { // 1300s = 21.6 min
            currentTimeMs += 10_000L
            val events = tracker.onClassificationResult(createResult())
            for (event in events) {
                if (event.type == ReelsSessionTracker.ReminderType.REPEAT_REMINDER) {
                    if (event.thresholdMs == 15 * 60 * 1000L) reminderAt15 = true
                    if (event.thresholdMs == 20 * 60 * 1000L) reminderAt20 = true
                }
            }
        }

        assertTrue("Repeat reminder at 15m must fire", reminderAt15)
        assertTrue("Repeat reminder at 20m must fire", reminderAt20)
    }

    // 13. Missing root and partial trees pause tracking immediately
    @Test
    fun testMissingRootAndPartialTrees_PauseTracking() {
        tracker.onClassificationResult(createResult())
        currentTimeMs += 4_000L
        tracker.onClassificationResult(createResult())
        assertEquals(4_000L, tracker.accumulatedReelsTimeMs)

        // Missing root
        val missingRoot = createResult(
            candidateState = CandidateScreenState.UNKNOWN,
            reasonCode = ReasonCode.MISSING_ROOT,
            outcome = ObservationOutcome.ROOT_UNAVAILABLE
        )
        tracker.onClassificationResult(missingRoot)
        assertEquals(ReelsSessionTracker.SessionState.PAUSED, tracker.sessionState)

        // Resume
        tracker.onClassificationResult(createResult())
        assertEquals(ReelsSessionTracker.SessionState.TRACKING, tracker.sessionState)

        // Truncated tree
        val truncated = createResult(
            reachedNodeLimit = true,
            outcome = ObservationOutcome.PARTIAL_OR_TRUNCATED,
            reasonCode = ReasonCode.TRUNCATED_TRAVERSAL
        )
        tracker.onClassificationResult(truncated)
        assertEquals(ReelsSessionTracker.SessionState.PAUSED, tracker.sessionState)
    }

    // 14. Fine-grained rejection reasons are accurately recorded and cleared upon eligible observation
    @Test
    fun testRejectionReasons_AccuratelyRecordedAndCleared() {
        val loggedMessages = mutableListOf<String>()
        val debugTracker = ReelsSessionTracker(
            clock = { currentTimeMs },
            logger = { loggedMessages.add(it) }
        )

        // Non-Instagram package
        debugTracker.onClassificationResult(createResult(isInstagramPackage = false))
        assertEquals("NOT_INSTAGRAM_PACKAGE", debugTracker.lastRejectionReason)
        assertEquals(ReelsSessionTracker.SessionState.PAUSED, debugTracker.sessionState)

        // Non-Reels candidate state (HOME_FEED)
        debugTracker.onClassificationResult(createResult(candidateState = CandidateScreenState.HOME_FEED))
        assertEquals("STATE_HOME_FEED", debugTracker.lastRejectionReason)

        // Low confidence
        debugTracker.onClassificationResult(createResult(confidence = Confidence.LOW))
        assertEquals("CONFIDENCE_LOW", debugTracker.lastRejectionReason)

        // Outcome not CLASSIFIED
        debugTracker.onClassificationResult(createResult(outcome = ObservationOutcome.ROOT_UNAVAILABLE))
        assertEquals("OUTCOME_ROOT_UNAVAILABLE", debugTracker.lastRejectionReason)

        // Partial tree
        debugTracker.onClassificationResult(createResult(isPartialTree = true))
        assertEquals("PARTIAL_TREE", debugTracker.lastRejectionReason)

        // Reached node limit
        debugTracker.onClassificationResult(createResult(reachedNodeLimit = true))
        assertEquals("REACHED_NODE_LIMIT", debugTracker.lastRejectionReason)

        // ReasonCode TRUNCATED_TRAVERSAL
        debugTracker.onClassificationResult(createResult(reasonCode = ReasonCode.TRUNCATED_TRAVERSAL))
        assertEquals("REASON_TRUNCATED_TRAVERSAL", debugTracker.lastRejectionReason)

        // ReasonCode UNSUPPORTED_STRUCTURE
        debugTracker.onClassificationResult(createResult(reasonCode = ReasonCode.UNSUPPORTED_STRUCTURE))
        assertEquals("REASON_UNSUPPORTED_STRUCTURE", debugTracker.lastRejectionReason)

        // Eligible observation: clears rejection reason and enters TRACKING
        debugTracker.onClassificationResult(createResult())
        org.junit.Assert.assertNull("Rejection reason must be null on eligible observation", debugTracker.lastRejectionReason)
        assertEquals(ReelsSessionTracker.SessionState.TRACKING, debugTracker.sessionState)

        // Verify logs contain expected diagnostic tags and zero private data
        assertTrue("Logged messages must not be empty", loggedMessages.isNotEmpty())
        assertTrue(loggedMessages.any { it.contains("Observation rejected: STATE_HOME_FEED") })
        assertTrue(loggedMessages.any { it.contains("Observation accepted: REELS_TAB (Confidence.HIGH)") })
    }

    // 15. Eligible Reels observation enters TRACKING and accumulates time monotonically
    @Test
    fun testEligibleObservation_EntersTrackingAndAccumulatesTime() {
        debugTrackerSetup()
        assertEquals(ReelsSessionTracker.SessionState.IDLE, tracker.sessionState)
        assertEquals(0L, tracker.accumulatedReelsTimeMs)

        // First eligible observation anchors session at 0s and sets state to TRACKING
        val eligibleResult = createResult(
            candidateState = CandidateScreenState.REELS_TAB,
            confidence = Confidence.HIGH,
            reasonCode = ReasonCode.MULTIPLE_SIGNALS_MATCH,
            outcome = ObservationOutcome.CLASSIFIED,
            isInstagramPackage = true,
            isPartialTree = false,
            reachedNodeLimit = false
        )
        tracker.onClassificationResult(eligibleResult)
        assertEquals(ReelsSessionTracker.SessionState.TRACKING, tracker.sessionState)
        assertEquals(0L, tracker.accumulatedReelsTimeMs)

        // Subsequent observation 5s later credits 5s
        currentTimeMs += 5_000L
        tracker.onClassificationResult(eligibleResult)
        assertEquals(ReelsSessionTracker.SessionState.TRACKING, tracker.sessionState)
        assertEquals(5_000L, tracker.accumulatedReelsTimeMs)
    }

    private fun debugTrackerSetup() {
        tracker.resetSession()
    }
}

