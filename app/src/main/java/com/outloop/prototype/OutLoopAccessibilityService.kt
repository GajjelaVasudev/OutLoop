package com.outloop.prototype

import android.accessibilityservice.AccessibilityService
import android.content.res.Resources
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.outloop.classifier.CandidateScreenState
import com.outloop.classifier.ClassificationResult
import com.outloop.classifier.Confidence
import com.outloop.classifier.ReasonCode
import com.outloop.classifier.ScreenClassifier
import com.outloop.prototype.repository.DiagnosticSessionRepository
import com.outloop.prototype.sanitizer.AccessibilityNodeSanitizer

/**
 * Minimal native Android AccessibilityService for inspecting Instagram layout structure.
 *
 * SCOPE & SAFETY RESTRICTIONS:
 * - Scoped strictly to `com.instagram.android`.
 * - Strictly observational: performs NO touch gestures, NO clicks, NO back navigation, and NO overlays.
 * - Hierarchy inspection is executed ONLY when a diagnostic session is actively running.
 * - Releases all AccessibilityNodeInfo references immediately after inspection.
 * - Never prints or records private text or personal content.
 */
class OutLoopAccessibilityService : AccessibilityService() {

    private val classifier = ScreenClassifier()
    private var lastInspectionUptimeMs = 0L

    companion object {
        const val TARGET_PACKAGE = "com.instagram.android"
        /** Throttle scans to avoid excessive hierarchy queries during rapid scrolling or animations. */
        const val INSPECTION_THROTTLE_MS = 250L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticSessionRepository.setServiceConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 1. Validate event and early-filter target package
        if (event == null) return
        val eventPackage = event.packageName?.toString()
        if (eventPackage != TARGET_PACKAGE) return

        // 2. Gate processing on active diagnostic session
        // If the user has not started or has stopped a session, do zero inspection!
        if (!DiagnosticSessionRepository.isSessionActive.value) {
            return
        }

        // 3. Throttle repeated hierarchy scans
        val now = SystemClock.uptimeMillis()
        if (now - lastInspectionUptimeMs < INSPECTION_THROTTLE_MS) {
            return
        }
        lastInspectionUptimeMs = now

        // 4. Retrieve active window root node safely
        var rootNode: AccessibilityNodeInfo? = null
        try {
            rootNode = rootInActiveWindow

            if (rootNode == null) {
                DiagnosticSessionRepository.recordResult(
                    ClassificationResult(
                        candidateState = CandidateScreenState.UNKNOWN,
                        confidence = Confidence.LOW,
                        reasonCode = ReasonCode.MISSING_ROOT,
                        timestamp = System.currentTimeMillis(),
                        evidenceExplanation = "System returned null root node for Instagram window.",
                        isDeviceVerified = false
                    )
                )
                return
            }

            // 5. Sanitize structural metadata without reading text or content descriptions
            val displayHeight = Resources.getSystem().displayMetrics.heightPixels
            val snapshot = AccessibilityNodeSanitizer.sanitize(rootNode, displayHeight)

            // 6. Classify sanitized structure
            val result = classifier.classify(snapshot)

            // 7. Record result in local diagnostic session
            DiagnosticSessionRepository.recordResult(result)

        } catch (e: Exception) {
            // Graceful exception handling: record diagnostic error without crashing or leaking state
            DiagnosticSessionRepository.recordResult(
                ClassificationResult(
                    candidateState = CandidateScreenState.UNKNOWN,
                    confidence = Confidence.LOW,
                    reasonCode = ReasonCode.UNSUPPORTED_STRUCTURE,
                    timestamp = System.currentTimeMillis(),
                    evidenceExplanation = "Exception during hierarchy inspection: ${e.javaClass.simpleName}",
                    isDeviceVerified = false
                )
            )
        } finally {
            // 8. Promptly release node references
            @Suppress("DEPRECATION")
            rootNode?.recycle()
        }
    }

    override fun onInterrupt() {
        // Required by AccessibilityService: gracefully handle service interruption
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        DiagnosticSessionRepository.setServiceConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        DiagnosticSessionRepository.setServiceConnected(false)
        super.onDestroy()
    }
}
