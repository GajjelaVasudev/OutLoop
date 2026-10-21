package com.outloop.classifier

/**
 * Diagnostic result output from [ScreenClassifier].
 *
 * All fields are strictly sanitized and safe for diagnostic UI display and in-memory logging.
 */
data class ClassificationResult(
    /** The inferred candidate screen state. */
    val candidateState: CandidateScreenState,

    /** The evidence confidence level (LOW, MEDIUM, HIGH). */
    val confidence: Confidence,

    /** Standardized machine-readable diagnostic code explaining the decision. */
    val reasonCode: ReasonCode,

    /** Epoch timestamp in milliseconds when classification occurred. */
    val timestamp: Long,

    /** Concise, sanitized structural explanation of the evidence. Contains zero user data. */
    val evidenceExplanation: String,

    /**
     * True if this classification rule has been verified with repeatable real-device evidence;
     * false if it is based on synthetic test fixtures or initial unverified structural assumptions.
     */
    val isDeviceVerified: Boolean,

    /**
     * Minimal, privacy-preserving structural diagnostic summary collected strictly during active sessions.
     * Contains zero user content, zero raw hierarchy, and zero private text.
     */
    val diagnosticSummary: DiagnosticSummary? = null
)

/**
 * Minimal, privacy-safe structural diagnostic metadata for on-device troubleshooting.
 */
data class DiagnosticSummary(
    val eventType: String? = null,
    val isInstagramPackage: Boolean = false,
    val hasRootNode: Boolean = false,
    val nodesVisited: Int = 0,
    val maxDepthReached: Int = 0,
    val reachedNodeLimit: Boolean = false,
    val reachedDepthLimit: Boolean = false,
    val hasBounds: Boolean = false,
    val bottomNavObservable: Boolean = false,
    val bottomNavChildCount: Int? = null,
    val selectedBottomTabOrdinal: Int? = null,
    val selectedTabId: String? = null,
    val hasFeedPostControls: Boolean = false,
    val observedClassCounts: Map<String, Int> = emptyMap(),
    val activeStructuralSignals: List<String> = emptyList(),
    val observationOutcome: ObservationOutcome = ObservationOutcome.NO_MATCHING_SIGNALS,
    val rootAcquisitionMethod: String = "NONE",
    val isPartialTree: Boolean = false,
    val structuralTreeDump: List<StructuralNodeRecord> = emptyList()
)

