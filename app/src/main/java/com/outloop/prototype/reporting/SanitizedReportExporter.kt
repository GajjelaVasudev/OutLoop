package com.outloop.prototype.reporting

import com.outloop.classifier.ClassificationResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Metadata and text result of a diagnostic report generation.
 */
data class ReportExportResult(
    /** The complete serialized report text. */
    val reportText: String,
    /** Number of events included in this report. */
    val eventCount: Int,
    /** Total events recorded in the repository session. */
    val totalRecordedCount: Int,
    /** Events evicted/dropped from the session due to capacity limits. */
    val droppedCount: Int,
    /** UTF-8 byte size of the report text. */
    val sizeBytes: Int,
    /** Total character count of the report text. */
    val charCount: Int
) {
    /** Human-readable size string (e.g. "45.2 KB"). */
    val formattedSize: String
        get() = String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0)
}

/**
 * Pure Kotlin exporter that formats sanitized diagnostic classification results
 * into comprehensive, privacy-preserving reports for clipboard copy and file export.
 *
 * PRIVACY GUARANTEES:
 * - ZERO accessibility node text, content descriptions, usernames, or message content.
 * - Exclusively records structural layout flags, bounds, resource IDs, and widget classes.
 */
object SanitizedReportExporter {

    /** Maximum payload size recommended for Android clipboard IPC without truncation risk (~500 KB). */
    const val SAFE_CLIPBOARD_BYTE_LIMIT = 500 * 1024

    /**
     * Generates a complete chronological report containing every event retained in the session.
     *
     * @param results The list of classification results to export.
     * @param totalRecordedCount The cumulative event count recorded in this session.
     * @param maxCapacity The maximum in-memory event retention limit.
     * @param isSessionActive Whether the diagnostic session is currently running.
     */
    fun generateSanitizedReport(
        results: List<ClassificationResult>,
        totalRecordedCount: Int = results.size,
        maxCapacity: Int = 1000,
        isSessionActive: Boolean = false
    ): ReportExportResult {
        if (results.isEmpty()) {
            val emptyMsg = "No diagnostic events recorded in this session."
            return ReportExportResult(
                reportText = emptyMsg,
                eventCount = 0,
                totalRecordedCount = totalRecordedCount,
                droppedCount = 0,
                sizeBytes = emptyMsg.toByteArray(Charsets.UTF_8).size,
                charCount = emptyMsg.length
            )
        }

        // Preserve chronological order (ascending timestamp)
        val chronoSorted = results.sortedBy { it.timestamp }
        val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val exportTimestamp = dateFormatter.format(Date())

        val droppedCount = maxOf(0, totalRecordedCount - results.size)
        val sb = StringBuilder(results.size * 256)

        sb.appendLine("=== OUTLOOP SANITIZED DIAGNOSTIC SESSION REPORT ===")
        sb.appendLine("Export Timestamp: $exportTimestamp")
        sb.appendLine("Session Status: ${if (isSessionActive) "ACTIVE (recording)" else "STOPPED"}")
        sb.appendLine("Total Events Recorded in Session: $totalRecordedCount")
        sb.appendLine("Events Exported in this Report: ${results.size}")
        if (droppedCount > 0) {
            sb.appendLine("WARNING: $droppedCount older events were evicted due to memory retention limit ($maxCapacity).")
        } else {
            sb.appendLine("History Retention: Complete session retained (all $totalRecordedCount events preserved, limit: $maxCapacity)")
        }
        sb.appendLine("Privacy Guarantee: ZERO private text, content descriptions, usernames, or message content recorded.")
        sb.appendLine("================================================================================")
        sb.appendLine()

        var clusterIndex = 1
        var lastTimestamp = 0L

        for ((index, item) in chronoSorted.withIndex()) {
            val deltaFromLast = if (lastTimestamp == 0L) 0L else (item.timestamp - lastTimestamp)
            val isNewCluster = lastTimestamp == 0L || deltaFromLast > 350L

            if (isNewCluster) {
                if (index > 0) sb.appendLine()
                sb.appendLine("[Transition Cluster #$clusterIndex - ${timeFormatter.format(Date(item.timestamp))}]")
                clusterIndex++
            }

            val diag = item.diagnosticSummary
            val eventType = diag?.eventType ?: "UNKNOWN_EVENT"
            val stateName = item.candidateState.displayName
            val reason = item.reasonCode.name
            val outcome = diag?.observationOutcome?.name ?: "N/A"
            val rootMethod = diag?.rootAcquisitionMethod ?: "NONE"
            val nodes = diag?.nodesVisited ?: 0
            val depth = diag?.maxDepthReached ?: 0
            val isPartial = diag?.isPartialTree ?: false
            val boundsValid = diag?.hasBounds ?: false

            val signals = if (diag?.activeStructuralSignals?.isNotEmpty() == true) {
                diag.activeStructuralSignals.joinToString(", ")
            } else {
                "None"
            }

            val nav = if (diag?.bottomNavObservable == true) {
                val sel = diag.selectedBottomTabOrdinal?.let { "Tab #$it" } ?: "No tab selected"
                val idStr = diag.selectedTabId?.let { " [$it]" } ?: ""
                "Observed (${diag.bottomNavChildCount ?: "?"} tabs, $sel$idStr)"
            } else {
                "Not observed"
            }

            sb.appendLine("  Event #${index + 1} (${timeFormatter.format(Date(item.timestamp))}, +${deltaFromLast}ms): $eventType")
            sb.appendLine("    Candidate State: $stateName ($reason) | Confidence: ${item.confidence.name}")
            sb.appendLine("    Outcome: $outcome | Root Method: $rootMethod | Partial Tree: $isPartial")
            sb.appendLine("    Hierarchy: $nodes nodes visited, max depth $depth | Bounds Valid: $boundsValid")
            sb.appendLine("    Bottom Navigation: $nav")
            if (diag?.hasFeedPostControls == true) {
                sb.appendLine("    Feed Post Controls: Observed")
            }
            sb.appendLine("    Signals: $signals")
            sb.appendLine("    Explanation: ${item.evidenceExplanation}")

            if (diag?.reachedNodeLimit == true || diag?.reachedDepthLimit == true) {
                sb.appendLine("    Notice: Traversal limit reached (nodes: ${diag.reachedNodeLimit}, depth: ${diag.reachedDepthLimit})")
            }
            if (diag?.structuralTreeDump?.isNotEmpty() == true) {
                sb.appendLine("    Tree Dump: ${diag.structuralTreeDump.size} structural node records captured")
            }

            lastTimestamp = item.timestamp
        }

        sb.appendLine()
        sb.appendLine("=== END OF SANITIZED REPORT (${results.size} events exported) ===")

        val fullText = sb.toString()
        val bytes = fullText.toByteArray(Charsets.UTF_8)
        return ReportExportResult(
            reportText = fullText,
            eventCount = results.size,
            totalRecordedCount = totalRecordedCount,
            droppedCount = droppedCount,
            sizeBytes = bytes.size,
            charCount = fullText.length
        )
    }

    /**
     * Generates a structural hierarchy report containing privacy-safe node-by-node dumps.
     * Includes all events that captured structural node trees without arbitrary truncation.
     */
    fun generateStructuralTreeReport(
        results: List<ClassificationResult>,
        totalRecordedCount: Int = results.size,
        maxCapacity: Int = 1000,
        isSessionActive: Boolean = false
    ): ReportExportResult {
        val chronoSorted = results.sortedBy { it.timestamp }
        val eventsWithTree = chronoSorted.filter { it.diagnosticSummary?.structuralTreeDump?.isNotEmpty() == true }

        if (eventsWithTree.isEmpty()) {
            val emptyMsg = "No detailed structural tree dumps recorded in this session (total events: ${results.size})."
            return ReportExportResult(
                reportText = emptyMsg,
                eventCount = 0,
                totalRecordedCount = totalRecordedCount,
                droppedCount = 0,
                sizeBytes = emptyMsg.toByteArray(Charsets.UTF_8).size,
                charCount = emptyMsg.length
            )
        }

        val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val sb = StringBuilder(eventsWithTree.size * 512)

        sb.appendLine("=== OUTLOOP PRIVACY-SAFE STRUCTURAL HIERARCHY REPORT ===")
        sb.appendLine("Export Timestamp: ${dateFormatter.format(Date())}")
        sb.appendLine("Session Status: ${if (isSessionActive) "ACTIVE (recording)" else "STOPPED"}")
        sb.appendLine("Total Events in Session: $totalRecordedCount | Events with Tree Dump: ${eventsWithTree.size} (Retention limit: $maxCapacity)")
        sb.appendLine("Privacy Notice: ZERO node text, content descriptions, usernames, or message content recorded.")
        sb.appendLine("================================================================================")
        sb.appendLine()

        for ((idx, item) in eventsWithTree.withIndex()) {
            val diag = item.diagnosticSummary ?: continue
            val tree = diag.structuralTreeDump

            sb.appendLine("--------------------------------------------------------------------------------")
            sb.appendLine("Tree Event #${idx + 1} at ${timeFormatter.format(Date(item.timestamp))}: ${diag.eventType ?: "UNKNOWN"}")
            sb.appendLine("  Classified State: ${item.candidateState.displayName} (${item.reasonCode.name}) | Outcome: ${diag.observationOutcome.name}")
            sb.appendLine("  Root Acquisition: ${diag.rootAcquisitionMethod} | Partial: ${diag.isPartialTree}")
            sb.appendLine("  Hierarchy Stats: ${diag.nodesVisited} nodes visited, max depth ${diag.maxDepthReached}")
            sb.appendLine("  Active Signals: ${if (diag.activeStructuralSignals.isNotEmpty()) diag.activeStructuralSignals.joinToString(", ") else "None"}")
            sb.appendLine("  Tree Records: ${tree.size} nodes captured")
            sb.appendLine("  [NODE-BY-NODE STRUCTURAL HIERARCHY]")

            for (node in tree) {
                val indent = "  " + "  ".repeat(node.depth.coerceAtMost(14))
                val parentStr = node.parentIndex?.let { "p:#$it" } ?: "p:ROOT"
                val idStr = node.resourceId?.let { "id:$it" } ?: "id:null"
                val flags = "clk:${node.isClickable} scr:${node.isScrollable} sel:${node.isSelected} vis:${node.isVisibleToUser}"
                val coll = node.collectionInfo?.let { " | coll:[$it]" } ?: ""
                val pager = node.pagerOrientation?.let { " | pager:$it" } ?: ""
                val status = if (node.traversalStatus != "VISITED") " | [${node.traversalStatus}]" else ""

                sb.appendLine("$indent#${node.nodeIndex} d:${node.depth} $parentStr ${node.className} | $idStr | bounds:${node.bounds} | ch:${node.childCount} | $flags$coll$pager$status")
            }
            sb.appendLine()
        }

        sb.appendLine("=== END OF STRUCTURAL REPORT (${eventsWithTree.size} trees exported) ===")

        val fullText = sb.toString()
        val bytes = fullText.toByteArray(Charsets.UTF_8)
        return ReportExportResult(
            reportText = fullText,
            eventCount = eventsWithTree.size,
            totalRecordedCount = totalRecordedCount,
            droppedCount = maxOf(0, totalRecordedCount - results.size),
            sizeBytes = bytes.size,
            charCount = fullText.length
        )
    }
}
