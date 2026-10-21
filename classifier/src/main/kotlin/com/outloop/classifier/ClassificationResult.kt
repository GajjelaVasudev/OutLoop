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
    val isDeviceVerified: Boolean
)
