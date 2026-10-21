package com.outloop.prototype.repository

import com.outloop.classifier.CandidateScreenState
import com.outloop.classifier.ClassificationResult
import com.outloop.classifier.Confidence
import com.outloop.classifier.DiagnosticSummary
import com.outloop.classifier.ObservationOutcome
import com.outloop.classifier.ReasonCode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DiagnosticSessionRepository] validating session lifecycle,
 * transition sequences, and missing-root observation handling.
 */
class DiagnosticSessionRepositoryTest {

    @Before
    fun setUp() {
        DiagnosticSessionRepository.setServiceConnected(true)
        DiagnosticSessionRepository.startSession()
        DiagnosticSessionRepository.clearResults()
    }

    @After
    fun tearDown() {
        DiagnosticSessionRepository.clearResults()
        DiagnosticSessionRepository.stopSession()
        DiagnosticSessionRepository.setServiceConnected(false)
    }

    private fun createResult(
        state: CandidateScreenState,
        reason: ReasonCode,
        outcome: ObservationOutcome,
        timestamp: Long,
        hasRoot: Boolean = true
    ): ClassificationResult {
        return ClassificationResult(
            candidateState = state,
            confidence = if (state == CandidateScreenState.UNKNOWN) Confidence.LOW else Confidence.HIGH,
            reasonCode = reason,
            timestamp = timestamp,
            evidenceExplanation = "Test observation: ${state.name}",
            isDeviceVerified = false,
            diagnosticSummary = DiagnosticSummary(
                eventType = "TYPE_WINDOW_CONTENT_CHANGED",
                isInstagramPackage = true,
                hasRootNode = hasRoot,
                nodesVisited = if (hasRoot) 50 else 0,
                observationOutcome = outcome
            )
        )
    }

    // Audit: Missing root must produce an unavailable observation and not be treated as fresh evidence for a new screen
    @Test
    fun testMissingRootProducesUnavailableObservation() {
        val missingRoot = createResult(
            state = CandidateScreenState.UNKNOWN,
            reason = ReasonCode.MISSING_ROOT,
            outcome = ObservationOutcome.ROOT_UNAVAILABLE,
            timestamp = 1000L,
            hasRoot = false
        )
        DiagnosticSessionRepository.recordResult(missingRoot)

        val results = DiagnosticSessionRepository.diagnosticResults.value
        assertEquals(1, results.size)
        val current = results.first()
        assertEquals(CandidateScreenState.UNKNOWN, current.candidateState)
        assertEquals(ReasonCode.MISSING_ROOT, current.reasonCode)
        assertEquals(ObservationOutcome.ROOT_UNAVAILABLE, current.diagnosticSummary?.observationOutcome)
        assertFalse(current.diagnosticSummary?.hasRootNode == true)
    }

    // Audit: Sequence of old-screen -> missing-root -> new-screen observations
    // must not let the old screen be presented as a confirmed current classification
    @Test
    fun testTransitionSequenceDoesNotPresentOldScreenDuringMissingRoot() {
        val feedEvent = createResult(
            state = CandidateScreenState.HOME_FEED,
            reason = ReasonCode.MULTIPLE_SIGNALS_MATCH,
            outcome = ObservationOutcome.CLASSIFIED,
            timestamp = 1000L
        )
        val missingRootEvent = createResult(
            state = CandidateScreenState.UNKNOWN,
            reason = ReasonCode.MISSING_ROOT,
            outcome = ObservationOutcome.ROOT_UNAVAILABLE,
            timestamp = 1300L,
            hasRoot = false
        )
        val reelsEvent = createResult(
            state = CandidateScreenState.REELS_TAB,
            reason = ReasonCode.MULTIPLE_SIGNALS_MATCH,
            outcome = ObservationOutcome.CLASSIFIED,
            timestamp = 1600L
        )

        // Step 1: Record Home Feed
        DiagnosticSessionRepository.recordResult(feedEvent)
        assertEquals(CandidateScreenState.HOME_FEED, DiagnosticSessionRepository.diagnosticResults.value.first().candidateState)

        // Step 2: Transition occurs, root is unavailable
        DiagnosticSessionRepository.recordResult(missingRootEvent)
        val currentDuringTransition = DiagnosticSessionRepository.diagnosticResults.value.first()
        // Must NOT present HOME_FEED as current!
        assertEquals(CandidateScreenState.UNKNOWN, currentDuringTransition.candidateState)
        assertEquals(ReasonCode.MISSING_ROOT, currentDuringTransition.reasonCode)
        assertEquals(ObservationOutcome.ROOT_UNAVAILABLE, currentDuringTransition.diagnosticSummary?.observationOutcome)

        // Historical event is still retained at index 1
        assertEquals(2, DiagnosticSessionRepository.diagnosticResults.value.size)
        assertEquals(CandidateScreenState.HOME_FEED, DiagnosticSessionRepository.diagnosticResults.value[1].candidateState)

        // Step 3: New screen settles on Reels
        DiagnosticSessionRepository.recordResult(reelsEvent)
        val currentSettled = DiagnosticSessionRepository.diagnosticResults.value.first()
        assertEquals(CandidateScreenState.REELS_TAB, currentSettled.candidateState)
        assertEquals(3, DiagnosticSessionRepository.diagnosticResults.value.size)
    }

    // Audit: Repeated identical observations must not create incorrect screen transitions
    @Test
    fun testRepeatedIdenticalObservationsMaintainConsistentState() {
        val reelsEvent1 = createResult(CandidateScreenState.REELS_TAB, ReasonCode.MULTIPLE_SIGNALS_MATCH, ObservationOutcome.CLASSIFIED, 1000L)
        val reelsEvent2 = createResult(CandidateScreenState.REELS_TAB, ReasonCode.MULTIPLE_SIGNALS_MATCH, ObservationOutcome.CLASSIFIED, 1250L)
        val reelsEvent3 = createResult(CandidateScreenState.REELS_TAB, ReasonCode.MULTIPLE_SIGNALS_MATCH, ObservationOutcome.CLASSIFIED, 1500L)

        DiagnosticSessionRepository.recordResult(reelsEvent1)
        DiagnosticSessionRepository.recordResult(reelsEvent2)
        DiagnosticSessionRepository.recordResult(reelsEvent3)

        val results = DiagnosticSessionRepository.diagnosticResults.value
        assertEquals(3, results.size)
        results.forEach { res ->
            assertEquals(CandidateScreenState.REELS_TAB, res.candidateState)
            assertEquals(ObservationOutcome.CLASSIFIED, res.diagnosticSummary?.observationOutcome)
        }
    }

    // Inactive session ignores records
    @Test
    fun testInactiveSessionDropsIncomingResults() {
        DiagnosticSessionRepository.stopSession()
        val feedEvent = createResult(CandidateScreenState.HOME_FEED, ReasonCode.MULTIPLE_SIGNALS_MATCH, ObservationOutcome.CLASSIFIED, 1000L)
        DiagnosticSessionRepository.recordResult(feedEvent)

        assertTrue(DiagnosticSessionRepository.diagnosticResults.value.isEmpty())
        assertEquals(0, DiagnosticSessionRepository.totalRecordedCount.value)
    }

    // Capacity and total recorded count tracking
    @Test
    fun testTotalRecordedCountAndSnapshot() {
        assertEquals(1000, DiagnosticSessionRepository.MAX_STORED_RESULTS)

        val e1 = createResult(CandidateScreenState.HOME_FEED, ReasonCode.MULTIPLE_SIGNALS_MATCH, ObservationOutcome.CLASSIFIED, 1000L)
        val e2 = createResult(CandidateScreenState.REELS_TAB, ReasonCode.MULTIPLE_SIGNALS_MATCH, ObservationOutcome.CLASSIFIED, 2000L)

        DiagnosticSessionRepository.recordResult(e1)
        DiagnosticSessionRepository.recordResult(e2)

        val snapshot = DiagnosticSessionRepository.getSnapshot()
        assertEquals(2, snapshot.retainedCount)
        assertEquals(2, snapshot.totalRecordedCount)
        assertEquals(0, snapshot.droppedCount)
        assertEquals(1000, snapshot.maxCapacity)
        assertTrue(snapshot.isSessionActive)

        // Clear resets both results and total count
        DiagnosticSessionRepository.clearResults()
        val clearedSnapshot = DiagnosticSessionRepository.getSnapshot()
        assertEquals(0, clearedSnapshot.retainedCount)
        assertEquals(0, clearedSnapshot.totalRecordedCount)
        assertEquals(0, clearedSnapshot.droppedCount)
    }
}
