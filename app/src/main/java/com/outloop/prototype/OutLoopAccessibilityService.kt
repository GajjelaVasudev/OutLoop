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
    private var restrictionOverlay: com.outloop.prototype.restriction.ReelsRestrictionOverlay? = null

    companion object {
        const val TARGET_PACKAGE = "com.instagram.android"
        /** Throttle scans to avoid excessive hierarchy queries during rapid scrolling or animations. */
        const val INSPECTION_THROTTLE_MS = 250L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticSessionRepository.setServiceConnected(true)
        com.outloop.prototype.reminder.ReelsReminderManager.initPreferences(this)
        restrictionOverlay = com.outloop.prototype.restriction.ReelsRestrictionOverlay(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 1. Validate event and early-filter target package
        if (event == null) return
        val eventPackage = event.packageName?.toString()
        if (eventPackage != TARGET_PACKAGE) {
            // Dismiss overlay immediately if user leaves Instagram
            restrictionOverlay?.dismiss()
            return
        }

        // 2. Ensure preferences are loaded and gate processing on active diagnostic session, active Reels reminders, or daily allowance
        com.outloop.prototype.reminder.ReelsReminderManager.initPreferences(this)
        val isDiagnosticActive = DiagnosticSessionRepository.isSessionActive.value
        val isReminderActive = com.outloop.prototype.reminder.ReelsReminderManager.isReminderEnabled.value
        val isAllowanceActive = com.outloop.prototype.allowance.ReelsAllowanceManager.getInstance(this).isAllowanceEnabled.value
        if (!isDiagnosticActive && !isReminderActive && !isAllowanceActive) {
            restrictionOverlay?.dismiss()
            return
        }

        val eventTypeString = try {
            AccessibilityEvent.eventTypeToString(event.eventType)
        } catch (_: Exception) {
            "EVENT_${event.eventType}"
        }
        val isWindowStateChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // 3. Throttle repeated content scans, but never drop window state transitions or retries after null root
        val now = SystemClock.uptimeMillis()
        if (!isWindowStateChanged && (now - lastInspectionUptimeMs < INSPECTION_THROTTLE_MS)) {
            return
        }

        // 4. Retrieve root node safely:
        // Attempt 1: rootInActiveWindow
        // Attempt 2: interactive windows list (windows property)
        // Attempt 3: event.source fallback
        var rootNode: AccessibilityNodeInfo? = null
        var isPartialTree = false
        var rootAcquisitionMethod = "NONE"

        var externalActivePackage: String? = null
        try {
            // Attempt 1: rootInActiveWindow
            val activeWindowRoot = rootInActiveWindow
            if (activeWindowRoot != null) {
                val pkg = activeWindowRoot.packageName?.toString()
                if (pkg == TARGET_PACKAGE) {
                    rootNode = activeWindowRoot
                    isPartialTree = false
                    rootAcquisitionMethod = "ROOT_IN_ACTIVE_WINDOW"
                } else {
                    externalActivePackage = pkg
                    // Active window belongs to keyboard (IME), system UI, or external package (e.g. OutLoop when user switches back)
                    @Suppress("DEPRECATION")
                    activeWindowRoot.recycle()
                }
            }

            // Attempt 2: If rootInActiveWindow was null or not target package, inspect interactive windows
            if (rootNode == null) {
                val interactiveWindows = try { windows } catch (_: Exception) { null }
                if (!interactiveWindows.isNullOrEmpty()) {
                    for (windowInfo in interactiveWindows) {
                        val windowRoot = try { windowInfo.root } catch (_: Exception) { null } ?: continue
                        if (windowRoot.packageName?.toString() == TARGET_PACKAGE) {
                            rootNode = windowRoot
                            isPartialTree = false
                            rootAcquisitionMethod = "INTERACTIVE_WINDOW_ROOT"
                            break
                        } else {
                            @Suppress("DEPRECATION")
                            windowRoot.recycle()
                        }
                    }
                }
            }

            // Attempt 3: If active window root and interactive windows were null or external,
            // safely evaluate event.source and verify target package
            if (rootNode == null) {
                val sourceNode = try { event.source } catch (_: Exception) { null }
                if (sourceNode != null) {
                    if (sourceNode.packageName?.toString() == TARGET_PACKAGE) {
                        var highestNode: AccessibilityNodeInfo = sourceNode
                        while (true) {
                            val parent = highestNode.parent ?: break
                            if (highestNode !== sourceNode) {
                                @Suppress("DEPRECATION")
                                highestNode.recycle()
                            }
                            highestNode = parent
                        }
                        rootNode = highestNode

                        val displayMetrics = Resources.getSystem().displayMetrics
                        val nodeBounds = android.graphics.Rect()
                        rootNode.getBoundsInScreen(nodeBounds)
                        val isWindowRoot = (nodeBounds.width() >= displayMetrics.widthPixels * 0.9 &&
                            nodeBounds.height() >= displayMetrics.heightPixels * 0.7) ||
                            rootNode.className?.toString()?.contains("DecorView") == true

                        isPartialTree = !isWindowRoot
                        rootAcquisitionMethod = if (isPartialTree) "EVENT_SOURCE_SUBTREE" else "EVENT_SOURCE_WINDOW_ROOT"
                    } else {
                        @Suppress("DEPRECATION")
                        sourceNode.recycle()
                    }
                }
            }

            if (rootNode == null) {
                val explanation = if (externalActivePackage != null) {
                    "No accessibility root: active window belongs to '$externalActivePackage' (Instagram in background or transitioning, event: $eventTypeString)."
                } else {
                    "No accessibility root available for Instagram window ($eventTypeString)."
                }
                // Do not update lastInspectionUptimeMs on null root so subsequent settled events are not throttled
                val nullRootResult = ClassificationResult(
                    candidateState = CandidateScreenState.UNKNOWN,
                    confidence = Confidence.LOW,
                    reasonCode = ReasonCode.MISSING_ROOT,
                    timestamp = System.currentTimeMillis(),
                    evidenceExplanation = explanation,
                    isDeviceVerified = false,
                    diagnosticSummary = com.outloop.classifier.DiagnosticSummary(
                        eventType = eventTypeString,
                        isInstagramPackage = true,
                        hasRootNode = false,
                        nodesVisited = 0,
                        observationOutcome = com.outloop.classifier.ObservationOutcome.ROOT_UNAVAILABLE,
                        rootAcquisitionMethod = "NONE"
                    )
                )
                DiagnosticSessionRepository.recordResult(nullRootResult)
                com.outloop.prototype.reminder.ReelsReminderManager.onClassificationResult(this, nullRootResult)
                com.outloop.prototype.allowance.ReelsAllowanceManager.onClassificationResult(this, nullRootResult)
                restrictionOverlay?.dismiss()
                return
            }

            // Successfully obtained a root node: update throttle timestamp
            lastInspectionUptimeMs = now

            // 5. Sanitize structural metadata without reading text or content descriptions
            val displayMetrics = Resources.getSystem().displayMetrics
            val displayHeight = displayMetrics.heightPixels
            val displayWidth = displayMetrics.widthPixels
            val snapshot = AccessibilityNodeSanitizer.sanitize(
                rootNode = rootNode,
                displayHeightPixels = displayHeight,
                displayWidthPixels = displayWidth,
                eventType = eventTypeString,
                isPartialTree = isPartialTree,
                rootAcquisitionMethod = rootAcquisitionMethod,
                captureTreeDump = isDiagnosticActive && DiagnosticSessionRepository.isDetailedTreeDumpEnabled.value
            )

            // 6. Classify sanitized structure
            val result = classifier.classify(snapshot)

            // 7. Record result in local diagnostic session and update reminder tracker
            DiagnosticSessionRepository.recordResult(result)
            com.outloop.prototype.reminder.ReelsReminderManager.onClassificationResult(this, result)

            // 8. Evaluate daily allowance and update restriction overlay
            val requiresRestriction = com.outloop.prototype.allowance.ReelsAllowanceManager.onClassificationResult(this, result)
            if (requiresRestriction) {
                val allowanceManager = com.outloop.prototype.allowance.ReelsAllowanceManager.getInstance(this)
                restrictionOverlay?.show(
                    allowanceMinutes = allowanceManager.dailyLimitMinutes.value,
                    accumulatedMs = allowanceManager.accumulatedDailyTimeMs
                )
            } else {
                restrictionOverlay?.dismiss()
            }

        } catch (e: Exception) {
            // Graceful exception handling: record diagnostic error without crashing or leaking state
            restrictionOverlay?.dismiss()
            val errResult = ClassificationResult(
                candidateState = CandidateScreenState.UNKNOWN,
                confidence = Confidence.LOW,
                reasonCode = ReasonCode.UNSUPPORTED_STRUCTURE,
                timestamp = System.currentTimeMillis(),
                evidenceExplanation = "Exception during hierarchy inspection: ${e.javaClass.simpleName}",
                isDeviceVerified = false,
                diagnosticSummary = com.outloop.classifier.DiagnosticSummary(
                    eventType = eventTypeString,
                    isInstagramPackage = true,
                    hasRootNode = rootNode != null,
                    nodesVisited = 0
                )
            )
            DiagnosticSessionRepository.recordResult(errResult)
            com.outloop.prototype.reminder.ReelsReminderManager.onClassificationResult(this, errResult)
            com.outloop.prototype.allowance.ReelsAllowanceManager.onClassificationResult(this, errResult)
        } finally {
            // 9. Promptly release node references
            @Suppress("DEPRECATION")
            rootNode?.recycle()
        }
    }

    override fun onInterrupt() {
        // Required by AccessibilityService: gracefully handle service interruption
        restrictionOverlay?.dismiss()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        restrictionOverlay?.dismiss()
        DiagnosticSessionRepository.setServiceConnected(false)
        com.outloop.prototype.reminder.ReelsReminderManager.syncState()
        com.outloop.prototype.allowance.ReelsAllowanceManager.getInstance(this).syncState()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        restrictionOverlay?.dismiss()
        restrictionOverlay = null
        DiagnosticSessionRepository.setServiceConnected(false)
        com.outloop.prototype.reminder.ReelsReminderManager.syncState()
        com.outloop.prototype.allowance.ReelsAllowanceManager.getInstance(this).syncState()
        super.onDestroy()
    }
}
