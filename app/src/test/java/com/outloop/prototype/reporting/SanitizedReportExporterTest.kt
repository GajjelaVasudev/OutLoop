package com.outloop.prototype.reporting

import com.outloop.classifier.CandidateScreenState
import com.outloop.classifier.ClassificationResult
import com.outloop.classifier.Confidence
import com.outloop.classifier.DiagnosticSummary
import com.outloop.classifier.ObservationOutcome
import com.outloop.classifier.ReasonCode
import com.outloop.classifier.StructuralNodeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying reliable complete diagnostic report generation,
 * formatting, ordering, retention reporting, and clipboard safety limits.
 */
class SanitizedReportExporterTest {

    private fun createSampleResult(
        state: CandidateScreenState,
        reason: ReasonCode,
        outcome: ObservationOutcome,
        timestamp: Long,
        hasRoot: Boolean = true,
        selectedTab: String? = null,
        signals: List<String> = emptyList(),
        treeDump: List<StructuralNodeRecord> = emptyList()
    ): ClassificationResult {
        return ClassificationResult(
            candidateState = state,
            confidence = Confidence.HIGH,
            reasonCode = reason,
            timestamp = timestamp,
            evidenceExplanation = "Test explanation for ${state.name}",
            isDeviceVerified = false,
            diagnosticSummary = DiagnosticSummary(
                eventType = "TYPE_WINDOW_CONTENT_CHANGED",
                isInstagramPackage = true,
                hasRootNode = hasRoot,
                nodesVisited = if (hasRoot) 80 else 0,
                maxDepthReached = if (hasRoot) 10 else 0,
                observationOutcome = outcome,
                rootAcquisitionMethod = if (hasRoot) "ROOT_IN_ACTIVE_WINDOW" else "NONE",
                selectedTabId = selectedTab,
                activeStructuralSignals = signals,
                structuralTreeDump = treeDump
            )
        )
    }

    // 1. All recorded events are included in the generated report
    @Test
    fun testAllRecordedEventsIncludedInReport() {
        val events = (1..30).map { i ->
            createSampleResult(
                state = CandidateScreenState.HOME_FEED,
                reason = ReasonCode.MULTIPLE_SIGNALS_MATCH,
                outcome = ObservationOutcome.CLASSIFIED,
                timestamp = 10000L + (i * 100L)
            )
        }

        val export = SanitizedReportExporter.generateSanitizedReport(
            results = events,
            totalRecordedCount = 30,
            maxCapacity = 1000
        )

        assertEquals(30, export.eventCount)
        assertEquals(30, export.totalRecordedCount)
        assertEquals(0, export.droppedCount)

        for (i in 1..30) {
            assertTrue("Report must contain Event #$i", export.reportText.contains("Event #$i "))
        }
    }

    // 2. More events than any existing UI display limit are still exported
    @Test
    fun testMoreEventsThanOldLimitAreExported() {
        // Generate 250 events (much higher than old 100 limit)
        val events = (1..250).map { i ->
            createSampleResult(
                state = CandidateScreenState.REELS_TAB,
                reason = ReasonCode.MULTIPLE_SIGNALS_MATCH,
                outcome = ObservationOutcome.CLASSIFIED,
                timestamp = 20000L + (i * 200L)
            )
        }

        val export = SanitizedReportExporter.generateSanitizedReport(
            results = events,
            totalRecordedCount = 250,
            maxCapacity = 1000
        )

        assertEquals(250, export.eventCount)
        assertTrue(export.reportText.contains("Events Exported in this Report: 250"))
        assertTrue(export.reportText.contains("Event #1 "))
        assertTrue(export.reportText.contains("Event #250 "))
    }

    // 3. Grouped transitions do not remove underlying events from the export
    @Test
    fun testGroupedTransitionsDoNotRemoveUnderlyingEvents() {
        // Cluster 1: events within 100ms
        val e1 = createSampleResult(CandidateScreenState.HOME_FEED, ReasonCode.MULTIPLE_SIGNALS_MATCH, ObservationOutcome.CLASSIFIED, 1000L)
        val e2 = createSampleResult(CandidateScreenState.HOME_FEED, ReasonCode.MULTIPLE_SIGNALS_MATCH, ObservationOutcome.CLASSIFIED, 1050L)
        // Cluster 2: event 500ms later
        val e3 = createSampleResult(CandidateScreenState.REELS_TAB, ReasonCode.MULTIPLE_SIGNALS_MATCH, ObservationOutcome.CLASSIFIED, 1600L)
        val e4 = createSampleResult(CandidateScreenState.REELS_TAB, ReasonCode.MULTIPLE_SIGNALS_MATCH, ObservationOutcome.CLASSIFIED, 1650L)

        val export = SanitizedReportExporter.generateSanitizedReport(listOf(e1, e2, e3, e4))

        assertEquals(4, export.eventCount)
        assertTrue(export.reportText.contains("[Transition Cluster #1"))
        assertTrue(export.reportText.contains("[Transition Cluster #2"))
        assertTrue(export.reportText.contains("Event #1 "))
        assertTrue(export.reportText.contains("Event #2 "))
        assertTrue(export.reportText.contains("Event #3 "))
        assertTrue(export.reportText.contains("Event #4 "))
    }

    // 4. Unknown and ROOT_UNAVAILABLE events are preserved
    @Test
    fun testUnknownAndRootUnavailableEventsPreserved() {
        val unknownMissingRoot = createSampleResult(
            state = CandidateScreenState.UNKNOWN,
            reason = ReasonCode.MISSING_ROOT,
            outcome = ObservationOutcome.ROOT_UNAVAILABLE,
            timestamp = 1000L,
            hasRoot = false
        )
        val unknownTruncated = createSampleResult(
            state = CandidateScreenState.UNKNOWN,
            reason = ReasonCode.TRUNCATED_TRAVERSAL,
            outcome = ObservationOutcome.PARTIAL_OR_TRUNCATED,
            timestamp = 1200L,
            hasRoot = true
        )

        val export = SanitizedReportExporter.generateSanitizedReport(listOf(unknownMissingRoot, unknownTruncated))

        assertEquals(2, export.eventCount)
        assertTrue(export.reportText.contains("Outcome: ROOT_UNAVAILABLE"))
        assertTrue(export.reportText.contains("MISSING_ROOT"))
        assertTrue(export.reportText.contains("Outcome: PARTIAL_OR_TRUNCATED"))
        assertTrue(export.reportText.contains("TRUNCATED_TRAVERSAL"))
    }

    // 5. Events appear in chronological order
    @Test
    fun testEventsAppearInChronologicalOrder() {
        val e1 = createSampleResult(CandidateScreenState.HOME_FEED, ReasonCode.MULTIPLE_SIGNALS_MATCH, ObservationOutcome.CLASSIFIED, 1000L)
        val e2 = createSampleResult(CandidateScreenState.REELS_TAB, ReasonCode.MULTIPLE_SIGNALS_MATCH, ObservationOutcome.CLASSIFIED, 2000L)
        val e3 = createSampleResult(CandidateScreenState.PROFILE, ReasonCode.KNOWN_STRUCTURE_MATCH, ObservationOutcome.CLASSIFIED, 3000L)

        // Pass in reverse chronological order (as repository stores them)
        val export = SanitizedReportExporter.generateSanitizedReport(listOf(e3, e2, e1))

        assertEquals(3, export.eventCount)
        val idx1 = export.reportText.indexOf("Event #1")
        val idx2 = export.reportText.indexOf("Event #2")
        val idx3 = export.reportText.indexOf("Event #3")

        assertTrue("Event #1 must appear before Event #2", idx1 in 0..<idx2)
        assertTrue("Event #2 must appear before Event #3", idx2 < idx3)
        // Verify Event #1 corresponds to e1 (1000L)
        assertTrue(export.reportText.contains("Event #1 (") && export.reportText.contains("Home Feed"))
    }

    // 6. Exported event count matches the number of events serialized
    @Test
    fun testExportedEventCountMatchesSerialized() {
        val events = (1..15).map { i ->
            createSampleResult(CandidateScreenState.DM_INBOX, ReasonCode.KNOWN_STRUCTURE_MATCH, ObservationOutcome.CLASSIFIED, 5000L + i * 100L)
        }
        val export = SanitizedReportExporter.generateSanitizedReport(events)

        val eventLineMatches = Regex("  Event #\\d+").findAll(export.reportText).count()
        assertEquals(15, export.eventCount)
        assertEquals(15, eventLineMatches)
    }

    // 7. Empty sessions are handled cleanly
    @Test
    fun testEmptySessionsHandledCleanly() {
        val export = SanitizedReportExporter.generateSanitizedReport(emptyList(), totalRecordedCount = 0)
        assertEquals(0, export.eventCount)
        assertEquals("No diagnostic events recorded in this session.", export.reportText)
    }

    // 8. Long reports are never silently truncated
    @Test
    fun testLongReportsNeverSilentlyTruncated() {
        val events = (1..500).map { i ->
            createSampleResult(CandidateScreenState.DM_CONVERSATION, ReasonCode.MULTIPLE_SIGNALS_MATCH, ObservationOutcome.CLASSIFIED, 10000L + i * 50L)
        }
        val export = SanitizedReportExporter.generateSanitizedReport(events, totalRecordedCount = 500, maxCapacity = 1000)

        assertEquals(500, export.eventCount)
        assertTrue("End marker must be present", export.reportText.endsWith("=== END OF SANITIZED REPORT (500 events exported) ===\n"))
        assertTrue("Event #500 must be present", export.reportText.contains("Event #500 "))
    }

    // 9. Sensitive raw accessibility content is excluded
    @Test
    fun testSensitiveRawAccessibilityContentExcluded() {
        val event = createSampleResult(
            state = CandidateScreenState.DM_CONVERSATION,
            reason = ReasonCode.MULTIPLE_SIGNALS_MATCH,
            outcome = ObservationOutcome.CLASSIFIED,
            timestamp = 1000L,
            signals = listOf("CHAT_INPUT", "DM_MESSAGE_LIST")
        )
        val export = SanitizedReportExporter.generateSanitizedReport(listOf(event))

        assertFalse("No text: field", export.reportText.contains("text:"))
        assertFalse("No contentDescription:", export.reportText.contains("contentDescription:"))
        assertFalse("No password:", export.reportText.contains("password"))
        assertTrue("Privacy notice must be present", export.reportText.contains("Privacy Guarantee: ZERO private text"))
    }

    // 10. Any retention limit or dropped events are explicitly reported
    @Test
    fun testRetentionLimitAndDroppedEventsExplicitlyReported() {
        val events = (1..1000).map { i ->
            createSampleResult(CandidateScreenState.HOME_FEED, ReasonCode.MULTIPLE_SIGNALS_MATCH, ObservationOutcome.CLASSIFIED, 1000L + i)
        }

        // Simulate 1250 total recorded events where 250 were dropped due to 1000 limit
        val export = SanitizedReportExporter.generateSanitizedReport(
            results = events,
            totalRecordedCount = 1250,
            maxCapacity = 1000
        )

        assertEquals(1000, export.eventCount)
        assertEquals(1250, export.totalRecordedCount)
        assertEquals(250, export.droppedCount)
        assertTrue(export.reportText.contains("WARNING: 250 older events were evicted due to memory retention limit (1000)."))
    }

    // Tree export tests: verify tree dumps are not arbitrarily truncated to 5
    @Test
    fun testTreeReportIncludesAllTreesWithoutArbitraryTruncation() {
        val trees = (1..12).map { i ->
            val nodeRecord = StructuralNodeRecord(
                nodeIndex = 0,
                parentIndex = null,
                depth = 0,
                className = "android.widget.FrameLayout",
                resourceId = "com.instagram.android:id/main_container",
                childCount = 1,
                isClickable = false,
                isScrollable = false,
                isSelected = false,
                isVisibleToUser = true,
                bounds = "[0,0][1080,2400]",
                traversalStatus = "VISITED"
            )
            createSampleResult(
                state = CandidateScreenState.STORIES,
                reason = ReasonCode.KNOWN_STRUCTURE_MATCH,
                outcome = ObservationOutcome.CLASSIFIED,
                timestamp = 1000L + (i * 200L),
                treeDump = listOf(nodeRecord)
            )
        }

        val export = SanitizedReportExporter.generateStructuralTreeReport(trees)

        // All 12 tree events must be exported, not just 5!
        assertEquals(12, export.eventCount)
        assertTrue(export.reportText.contains("Tree Event #1 at"))
        assertTrue(export.reportText.contains("Tree Event #12 at"))
    }
}
