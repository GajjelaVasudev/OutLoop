package com.outloop.prototype

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Resources
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.outloop.classifier.CandidateScreenState
import com.outloop.classifier.ClassificationResult
import com.outloop.classifier.Confidence
import com.outloop.classifier.ReasonCode
import com.outloop.classifier.ScreenClassifier
import com.outloop.prototype.repository.DiagnosticSessionRepository
import com.outloop.prototype.sanitizer.AccessibilityNodeSanitizer

/**
 * Native Android AccessibilityService for inspecting Instagram layout structure,
 * managing daily allowance restrictions, and continuous reels break interventions.
 *
 * SCOPE & SAFETY RESTRICTIONS:
 * - Scoped strictly to `com.instagram.android`.
 * - Strictly observational of user content: never reads or logs private messages, user text, or passwords.
 * - Releases all AccessibilityNodeInfo references immediately after inspection.
 * - Single-owner lifecycle architecture for overlays, timers, and audio-focus states.
 */
class OutLoopAccessibilityService : AccessibilityService() {

    enum class OverlayLifecycleState {
        HIDDEN,
        SHOWING_DAILY_RESTRICTION,
        SHOWING_CONTINUOUS_BREAK,
        TEMPORARILY_OUTSIDE_INSTAGRAM
    }

    private val classifier = ScreenClassifier()
    private var lastInspectionUptimeMs = 0L
    private var restrictionOverlay: com.outloop.prototype.restriction.ReelsRestrictionOverlay? = null
    private var continuousBreakOverlay: com.outloop.prototype.continuous.ContinuousBreakOverlay? = null
    private val playbackController by lazy { com.outloop.prototype.playback.ReelsPlaybackController(this) }

    private var overlayLifecycleState = OverlayLifecycleState.HIDDEN
    private var stateBeforeDeparture: OverlayLifecycleState? = null
    private var departureConfirmationCount = 0

    val currentOverlayState: OverlayLifecycleState
        get() = overlayLifecycleState

    private val scheduledCheckHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isScheduledCheckRunning = false

    private val sessionExitHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sessionExitRunnable = Runnable {
        android.util.Log.d("OutLoopBreakDebug", "Session exit grace period expired (10s). Resetting continuous session.")
        com.outloop.prototype.continuous.ContinuousBreakManager.getInstance(this@OutLoopAccessibilityService)
            .resetSession("Left Instagram for longer than grace period")
        stateBeforeDeparture = null
    }

    private val overlayWatchdogHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isWatchdogRunning = false
    private val overlayWatchdogRunnable = object : Runnable {
        override fun run() {
            if (!isWatchdogRunning) return
            val anyShowing = (restrictionOverlay?.isShowing == true || continuousBreakOverlay?.isShowing == true)
            if (!anyShowing) {
                isWatchdogRunning = false
                return
            }

            if (checkActiveWindowDeparture()) {
                departureConfirmationCount++
                val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                val isLocked = powerManager?.isInteractive == false || keyguardManager?.isKeyguardLocked == true

                // Immediate departure for screen off / keyguard; 2 consecutive checks (~150-300ms) for app switches
                if (isLocked || departureConfirmationCount >= 2) {
                    android.util.Log.d("OutLoopBreakDebug", "Watchdog confirmed departure from Instagram. Transitioning outside.")
                    departureConfirmationCount = 0
                    handleDepartureToOutsideInstagram("Watchdog departure confirmed")
                    return
                }
            } else {
                departureConfirmationCount = 0
            }

            if (isWatchdogRunning) {
                overlayWatchdogHandler.postDelayed(this, 150L)
            }
        }
    }

    private fun startOverlayWatchdog() {
        if (isWatchdogRunning) return
        isWatchdogRunning = true
        departureConfirmationCount = 0
        overlayWatchdogHandler.removeCallbacks(overlayWatchdogRunnable)
        overlayWatchdogHandler.postDelayed(overlayWatchdogRunnable, 150L)
    }

    private fun stopOverlayWatchdog() {
        isWatchdogRunning = false
        departureConfirmationCount = 0
        overlayWatchdogHandler.removeCallbacks(overlayWatchdogRunnable)
    }

    private fun showDailyRestriction(allowanceMinutes: Long, accumulatedMs: Long, rootNode: AccessibilityNodeInfo?) {
        if (overlayLifecycleState == OverlayLifecycleState.SHOWING_DAILY_RESTRICTION && restrictionOverlay?.isShowing == true) {
            return
        }
        if (continuousBreakOverlay?.isShowing == true) {
            continuousBreakOverlay?.dismiss("Daily restriction takes precedence")
        }
        stopScheduledCheck("Daily restriction active")
        overlayLifecycleState = OverlayLifecycleState.SHOWING_DAILY_RESTRICTION
        stateBeforeDeparture = null
        playbackController.suppressPlayback(
            rootNode,
            isVerifiedTargetPackage = true,
            isReelsContext = true,
            cause = com.outloop.prototype.playback.PlaybackRestrictionCause.DAILY_ALLOWANCE
        )
        restrictionOverlay?.show(allowanceMinutes, accumulatedMs)
        startOverlayWatchdog()
    }

    private fun showContinuousBreak(intervalMinutes: Int, rootNode: AccessibilityNodeInfo?) {
        if (overlayLifecycleState == OverlayLifecycleState.SHOWING_DAILY_RESTRICTION) {
            return // Daily restriction takes precedence over continuous break
        }
        if (overlayLifecycleState == OverlayLifecycleState.SHOWING_CONTINUOUS_BREAK && continuousBreakOverlay?.isShowing == true) {
            return
        }
        stopScheduledCheck("Continuous break prompt active")
        overlayLifecycleState = OverlayLifecycleState.SHOWING_CONTINUOUS_BREAK
        stateBeforeDeparture = null
        playbackController.suppressPlayback(
            rootNode,
            isVerifiedTargetPackage = true,
            isReelsContext = true,
            cause = com.outloop.prototype.playback.PlaybackRestrictionCause.CONTINUOUS_BREAK
        )
        continuousBreakOverlay?.showPrompt(intervalMinutes)
        startOverlayWatchdog()
    }

    private fun handleDepartureToOutsideInstagram(reason: String) {
        if (overlayLifecycleState == OverlayLifecycleState.TEMPORARILY_OUTSIDE_INSTAGRAM) {
            return
        }
        if (overlayLifecycleState == OverlayLifecycleState.SHOWING_DAILY_RESTRICTION ||
            overlayLifecycleState == OverlayLifecycleState.SHOWING_CONTINUOUS_BREAK
        ) {
            stateBeforeDeparture = overlayLifecycleState
        }
        overlayLifecycleState = OverlayLifecycleState.TEMPORARILY_OUTSIDE_INSTAGRAM
        departureConfirmationCount = 0

        // Immediately detach both overlays so Recent Apps, launcher, or other apps are 100% usable
        restrictionOverlay?.dismiss()
        continuousBreakOverlay?.dismiss("Departure: $reason")
        playbackController.releaseAllSuppression()
        stopOverlayWatchdog()
        stopScheduledCheck("Outside Instagram")

        // Start 10-second grace timer for continuous session
        sessionExitHandler.removeCallbacks(sessionExitRunnable)
        sessionExitHandler.postDelayed(sessionExitRunnable, 10_000L)
        android.util.Log.d("OutLoopBreakDebug", "Transitioned to TEMPORARILY_OUTSIDE_INSTAGRAM ($reason)")
    }

    private fun hideAllOverlays(reason: String) {
        overlayLifecycleState = OverlayLifecycleState.HIDDEN
        stateBeforeDeparture = null
        departureConfirmationCount = 0
        restrictionOverlay?.dismiss()
        continuousBreakOverlay?.dismiss(reason)
        playbackController.releaseAllSuppression()
        stopOverlayWatchdog()
    }

    /**
     * Inspects accessibility windows to identify the current top-level application window.
     * Filters out accessibility overlays (such as OutLoop's own overlay), IMEs, and system bars.
     */
    fun getForegroundAppPackage(): String? {
        val windowList = try { windows } catch (_: Exception) { null }
        if (!windowList.isNullOrEmpty()) {
            val appWindows = windowList.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            val activeApp = appWindows.find { it.isActive || it.isFocused }
                ?: appWindows.maxByOrNull { it.layer }
            val windowRoot = try { activeApp?.root } catch (_: Exception) { null }
            val pkg = windowRoot?.packageName?.toString()
            @Suppress("DEPRECATION")
            windowRoot?.recycle()
            if (pkg != null) return pkg
        }
        val activeRoot = try { rootInActiveWindow } catch (_: Exception) { null }
        if (activeRoot != null) {
            val pkg = activeRoot.packageName?.toString()
            @Suppress("DEPRECATION")
            activeRoot.recycle()
            if (pkg != null && pkg != packageName) {
                return pkg
            }
        }
        return null
    }

    private fun checkActiveWindowDeparture(): Boolean {
        // 1. Device interactive / lock state check
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (powerManager?.isInteractive == false || keyguardManager?.isKeyguardLocked == true) {
            return true
        }

        // 2. Inspect foreground application package
        val fgPkg = getForegroundAppPackage()
        if (fgPkg != null) {
            if (fgPkg != TARGET_PACKAGE && fgPkg != packageName) {
                return true
            }
        }

        return false
    }

    private val scheduledCheckRunnable = object : Runnable {
        override fun run() {
            if (!isScheduledCheckRunning) return
            if (overlayLifecycleState == OverlayLifecycleState.SHOWING_CONTINUOUS_BREAK ||
                overlayLifecycleState == OverlayLifecycleState.SHOWING_DAILY_RESTRICTION
            ) {
                // Overlay is currently showing; pulse wait without triggering duplicate prompts
                if (isScheduledCheckRunning) {
                    scheduledCheckHandler.postDelayed(this, 1000L)
                }
                return
            }
            val continuousBreakManager = com.outloop.prototype.continuous.ContinuousBreakManager.getInstance(this@OutLoopAccessibilityService)
            if (!continuousBreakManager.isBreakReminderEnabled.value || !continuousBreakManager.isContinuousSessionActive) {
                stopScheduledCheck("Continuous break reminder disabled or session inactive")
                return
            }

            val activeRoot = try { rootInActiveWindow } catch (_: Exception) { null }
            if (activeRoot != null) {
                val pkg = activeRoot.packageName?.toString()
                if (pkg == TARGET_PACKAGE) {
                    val displayMetrics = Resources.getSystem().displayMetrics
                    val snapshot = AccessibilityNodeSanitizer.sanitize(
                        rootNode = activeRoot,
                        displayHeightPixels = displayMetrics.heightPixels,
                        displayWidthPixels = displayWidthPixels(displayMetrics),
                        eventType = "SCHEDULED_CHECK",
                        isPartialTree = false,
                        rootAcquisitionMethod = "SCHEDULED_ROOT"
                    )
                    val result = classifier.classify(snapshot)
                    if (result.candidateState == CandidateScreenState.REELS_TAB) {
                        val now = SystemClock.elapsedRealtime()
                        continuousBreakManager.updateLatestObservationTime(now)
                        val triggered = continuousBreakManager.checkThresholdReached(now)
                        if (triggered) {
                            android.util.Log.d("OutLoopBreakDebug", "Threshold reached via scheduled check. Triggering break prompt for ${continuousBreakManager.breakIntervalMinutes.value}m.")
                            showContinuousBreak(continuousBreakManager.breakIntervalMinutes.value, activeRoot)
                            @Suppress("DEPRECATION")
                            activeRoot.recycle()
                            return
                        }
                    } else if (continuousBreakManager.isDefinitiveExitFromReels(result)) {
                        android.util.Log.d("OutLoopBreakDebug", "Scheduled check observed definitive non-Reels screen: ${result.candidateState}. Resetting session.")
                        continuousBreakManager.resetSession("Scheduled check observed ${result.candidateState}")
                        hideAllOverlays("Left Reels (${result.candidateState})")
                        stopScheduledCheck("Screen no longer Reels")
                        @Suppress("DEPRECATION")
                        activeRoot.recycle()
                        return
                    } else {
                        android.util.Log.d("OutLoopBreakDebug", "Scheduled check observed transient state: ${result.candidateState} (${result.reasonCode}). Maintaining continuous session.")
                        val now = SystemClock.elapsedRealtime()
                        val triggered = continuousBreakManager.checkThresholdReached(now)
                        if (triggered) {
                            android.util.Log.d("OutLoopBreakDebug", "Threshold reached during transient pulse. Triggering break prompt for ${continuousBreakManager.breakIntervalMinutes.value}m.")
                            showContinuousBreak(continuousBreakManager.breakIntervalMinutes.value, activeRoot)
                            @Suppress("DEPRECATION")
                            activeRoot.recycle()
                            return
                        }
                    }
                } else if (pkg == packageName) {
                    // OutLoop overlay active
                } else {
                    android.util.Log.d("OutLoopBreakDebug", "Scheduled check observed active window belonging to '$pkg'. Dismissing overlays.")
                    handleDepartureToOutsideInstagram("Active window not Instagram: $pkg")
                    @Suppress("DEPRECATION")
                    activeRoot.recycle()
                    return
                }
                @Suppress("DEPRECATION")
                activeRoot.recycle()
            }

            if (isScheduledCheckRunning) {
                scheduledCheckHandler.postDelayed(this, 1000L)
            }
        }
    }

    private fun displayWidthPixels(metrics: android.util.DisplayMetrics): Int = metrics.widthPixels

    private fun startScheduledCheck() {
        if (isScheduledCheckRunning) return
        isScheduledCheckRunning = true
        scheduledCheckHandler.removeCallbacks(scheduledCheckRunnable)
        scheduledCheckHandler.postDelayed(scheduledCheckRunnable, 1000L)
        android.util.Log.d("OutLoopBreakDebug", "Continuous break scheduled check started (1s pulse).")
    }

    private fun stopScheduledCheck(reason: String = "Stopped") {
        if (!isScheduledCheckRunning) return
        isScheduledCheckRunning = false
        scheduledCheckHandler.removeCallbacks(scheduledCheckRunnable)
        android.util.Log.d("OutLoopBreakDebug", "Continuous break scheduled check stopped. Reason: $reason")
    }

    companion object {
        const val TARGET_PACKAGE = "com.instagram.android"
        /** Throttle scans to avoid excessive hierarchy queries during rapid scrolling or animations. */
        const val INSPECTION_THROTTLE_MS = 250L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticSessionRepository.setServiceConnected(true)
        com.outloop.prototype.reminder.ReelsReminderManager.initPreferences(this)
        restrictionOverlay = com.outloop.prototype.restriction.ReelsRestrictionOverlay(this, playbackController)
        com.outloop.prototype.allowance.ReelsAllowanceManager.getInstance(this).onRestrictionDismissRequested = {
            hideAllOverlays("Allowance manager requested dismissal")
        }
        continuousBreakOverlay = com.outloop.prototype.continuous.ContinuousBreakOverlay(this, playbackController)
        val continuousBreakManager = com.outloop.prototype.continuous.ContinuousBreakManager.getInstance(this)
        continuousBreakManager.onDismissPromptRequested = {
            val allowanceManager = com.outloop.prototype.allowance.ReelsAllowanceManager.getInstance(this)
            if (allowanceManager.isAllowanceExhausted) {
                continuousBreakOverlay?.dismiss("Break prompt dismissed; daily restriction remains active")
                playbackController.releaseSuppression(com.outloop.prototype.playback.PlaybackRestrictionCause.CONTINUOUS_BREAK)
                if (overlayLifecycleState != OverlayLifecycleState.SHOWING_DAILY_RESTRICTION) {
                    showDailyRestriction(
                        allowanceMinutes = allowanceManager.effectiveLimitMinutes.value,
                        accumulatedMs = allowanceManager.accumulatedDailyTimeMs,
                        rootNode = null
                    )
                }
            } else {
                hideAllOverlays("Break manager requested prompt dismissal")
            }
        }
        continuousBreakManager.onSessionStateChanged = { active ->
            if (active && overlayLifecycleState != OverlayLifecycleState.SHOWING_CONTINUOUS_BREAK &&
                overlayLifecycleState != OverlayLifecycleState.SHOWING_DAILY_RESTRICTION
            ) {
                startScheduledCheck()
            } else {
                stopScheduledCheck("Session state changed")
            }
        }
        continuousBreakManager.onResumeScheduledCheckRequested = {
            if (overlayLifecycleState != OverlayLifecycleState.SHOWING_CONTINUOUS_BREAK &&
                overlayLifecycleState != OverlayLifecycleState.SHOWING_DAILY_RESTRICTION
            ) {
                startScheduledCheck()
            }
        }
        android.util.Log.d(
            "OutLoopBreakDebug",
            "AccessibilityService connected. Break reminders enabled=${continuousBreakManager.isBreakReminderEnabled.value}, interval=${continuousBreakManager.breakIntervalMinutes.value}m"
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventPackage = event.packageName?.toString()
        val eventClass = event.className?.toString()
        val eventType = event.eventType

        // 1. OutLoop's own package events
        if (eventPackage == packageName) {
            // If the user navigated to OutLoop's MainActivity (dashboard), dismiss overlays so user can interact
            if (eventClass?.contains("MainActivity") == true) {
                handleDepartureToOutsideInstagram("Opened OutLoop MainActivity")
            }
            return
        }

        // 2. Window hierarchy changes (TYPE_WINDOWS_CHANGED)
        // Do not classify every TYPE_WINDOWS_CHANGED as leaving Instagram!
        if (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val fgPkg = getForegroundAppPackage()
            if (fgPkg != null && fgPkg != TARGET_PACKAGE && fgPkg != packageName) {
                handleDepartureToOutsideInstagram("Foreground app is external: $fgPkg")
            }
            return
        }

        // 3. External package events
        if (eventPackage != TARGET_PACKAGE) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val fgPkg = getForegroundAppPackage()
                if (fgPkg != null && fgPkg != TARGET_PACKAGE && fgPkg != packageName) {
                    handleDepartureToOutsideInstagram("Window state changed to external package: $eventPackage")
                }
            }
            // Ignore non-WINDOW_STATE_CHANGED events from external packages (e.g. system UI notifications, IME keyboard)
            return
        }

        // 4. Target package confirmed: com.instagram.android
        sessionExitHandler.removeCallbacks(sessionExitRunnable)
        departureConfirmationCount = 0

        // Ensure preferences are loaded and gate processing on active features
        com.outloop.prototype.reminder.ReelsReminderManager.initPreferences(this)
        val isDiagnosticActive = DiagnosticSessionRepository.isSessionActive.value
        val isReminderActive = com.outloop.prototype.reminder.ReelsReminderManager.isReminderEnabled.value
        val isAllowanceActive = com.outloop.prototype.allowance.ReelsAllowanceManager.getInstance(this).isAllowanceEnabled.value
        val isContinuousBreakActive = com.outloop.prototype.continuous.ContinuousBreakManager.getInstance(this).isBreakReminderEnabled.value
        if (!isDiagnosticActive && !isReminderActive && !isAllowanceActive && !isContinuousBreakActive) {
            stopScheduledCheck("No active OutLoop features")
            hideAllOverlays("No active OutLoop features")
            return
        }

        val eventTypeString = try {
            AccessibilityEvent.eventTypeToString(event.eventType)
        } catch (_: Exception) {
            "EVENT_${event.eventType}"
        }
        val isWindowStateChanged = (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)

        // Throttle repeated content scans, but never drop window state transitions
        val now = SystemClock.uptimeMillis()
        if (!isWindowStateChanged && (now - lastInspectionUptimeMs < INSPECTION_THROTTLE_MS)) {
            return
        }

        var rootNode: AccessibilityNodeInfo? = null
        var isPartialTree = false
        var rootAcquisitionMethod = "NONE"

        try {
            // Attempt 1: rootInActiveWindow
            val activeWindowRoot = try { rootInActiveWindow } catch (_: Exception) { null }
            if (activeWindowRoot != null) {
                val pkg = activeWindowRoot.packageName?.toString()
                if (pkg == TARGET_PACKAGE) {
                    rootNode = activeWindowRoot
                    isPartialTree = false
                    rootAcquisitionMethod = "ROOT_IN_ACTIVE_WINDOW"
                } else {
                    @Suppress("DEPRECATION")
                    activeWindowRoot.recycle()
                }
            }

            // Attempt 2: interactive windows list
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

            // Attempt 3: event.source fallback
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
                val nullRootResult = ClassificationResult(
                    candidateState = CandidateScreenState.UNKNOWN,
                    confidence = Confidence.LOW,
                    reasonCode = ReasonCode.MISSING_ROOT,
                    timestamp = System.currentTimeMillis(),
                    evidenceExplanation = "No accessibility root available for Instagram window ($eventTypeString).",
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
                com.outloop.prototype.continuous.ContinuousBreakManager.getInstance(this).onClassificationResult(nullRootResult)
                return
            }

            // Successfully obtained a root node: update throttle timestamp
            lastInspectionUptimeMs = now

            val displayMetrics = Resources.getSystem().displayMetrics
            val snapshot = AccessibilityNodeSanitizer.sanitize(
                rootNode = rootNode,
                displayHeightPixels = displayMetrics.heightPixels,
                displayWidthPixels = displayWidthPixels(displayMetrics),
                eventType = eventTypeString,
                isPartialTree = isPartialTree,
                rootAcquisitionMethod = rootAcquisitionMethod,
                captureTreeDump = isDiagnosticActive && DiagnosticSessionRepository.isDetailedTreeDumpEnabled.value
            )

            val result = classifier.classify(snapshot)
            val isVerifiedReels = (result.candidateState == CandidateScreenState.REELS_TAB)

            DiagnosticSessionRepository.recordResult(result)
            com.outloop.prototype.reminder.ReelsReminderManager.onClassificationResult(this, result)

            val allowanceManager = com.outloop.prototype.allowance.ReelsAllowanceManager.getInstance(this)
            val requiresDailyRestriction = com.outloop.prototype.allowance.ReelsAllowanceManager.onClassificationResult(this, result)

            val continuousBreakManager = com.outloop.prototype.continuous.ContinuousBreakManager.getInstance(this)
            val shouldShowContinuousBreak = continuousBreakManager.onClassificationResult(result)

            if (requiresDailyRestriction) {
                // Priority 1: Daily restriction takes absolute precedence over continuous break
                showDailyRestriction(
                    allowanceMinutes = allowanceManager.effectiveLimitMinutes.value,
                    accumulatedMs = allowanceManager.accumulatedDailyTimeMs,
                    rootNode = rootNode
                )
            } else {
                if (overlayLifecycleState == OverlayLifecycleState.SHOWING_DAILY_RESTRICTION) {
                    hideAllOverlays("Daily restriction no longer required")
                }

                val shouldRestoreBreakPrompt = (stateBeforeDeparture == OverlayLifecycleState.SHOWING_CONTINUOUS_BREAK ||
                    continuousBreakManager.isPromptEligible.value) &&
                    continuousBreakManager.isContinuousSessionActive &&
                    isVerifiedReels

                if (shouldShowContinuousBreak || shouldRestoreBreakPrompt) {
                    showContinuousBreak(continuousBreakManager.breakIntervalMinutes.value, rootNode)
                } else if (overlayLifecycleState == OverlayLifecycleState.SHOWING_CONTINUOUS_BREAK) {
                    // Currently showing continuous break prompt / Mind Refresh on Reels: keep showing
                } else if (continuousBreakManager.isDefinitiveExitFromReels(result)) {
                    stopScheduledCheck("Definitive exit: ${result.candidateState}")
                    hideAllOverlays("Definitive exit from Reels: ${result.candidateState}")
                } else if (continuousBreakManager.isContinuousSessionActive && !isScheduledCheckRunning) {
                    overlayLifecycleState = OverlayLifecycleState.HIDDEN
                    startScheduledCheck()
                } else {
                    overlayLifecycleState = OverlayLifecycleState.HIDDEN
                }
            }

        } catch (e: Exception) {
            stopScheduledCheck("Exception during hierarchy inspection: ${e.javaClass.simpleName}")
            hideAllOverlays("Exception during hierarchy inspection: ${e.javaClass.simpleName}")
            val errResult = ClassificationResult(
                candidateState = CandidateScreenState.UNKNOWN,
                confidence = Confidence.LOW,
                reasonCode = ReasonCode.UNSUPPORTED_STRUCTURE,
                timestamp = System.currentTimeMillis(),
                evidenceExplanation = "Exception during hierarchy inspection: ${e.javaClass.simpleName}: ${e.message}",
                isDeviceVerified = false,
                diagnosticSummary = com.outloop.classifier.DiagnosticSummary(
                    eventType = eventTypeString,
                    isInstagramPackage = true,
                    hasRootNode = rootNode != null,
                    nodesVisited = 0,
                    observationOutcome = com.outloop.classifier.ObservationOutcome.ROOT_UNAVAILABLE,
                    rootAcquisitionMethod = "NONE"
                )
            )
            DiagnosticSessionRepository.recordResult(errResult)
            com.outloop.prototype.reminder.ReelsReminderManager.onClassificationResult(this, errResult)
            com.outloop.prototype.allowance.ReelsAllowanceManager.onClassificationResult(this, errResult)
            com.outloop.prototype.continuous.ContinuousBreakManager.getInstance(this).onClassificationResult(errResult)
        } finally {
            if (rootNode != null) {
                @Suppress("DEPRECATION")
                rootNode.recycle()
            }
        }
    }

    override fun onInterrupt() {
        stopScheduledCheck("Service interrupted")
        sessionExitHandler.removeCallbacks(sessionExitRunnable)
        hideAllOverlays("Service interrupted")
        com.outloop.prototype.continuous.ContinuousBreakManager.getInstance(this).resetSession("Service interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        stopScheduledCheck("Service unbind")
        sessionExitHandler.removeCallbacks(sessionExitRunnable)
        hideAllOverlays("Service unbind")
        DiagnosticSessionRepository.setServiceConnected(false)
        com.outloop.prototype.reminder.ReelsReminderManager.syncState()
        val allowanceManager = com.outloop.prototype.allowance.ReelsAllowanceManager.getInstance(this)
        allowanceManager.onRestrictionDismissRequested = null
        allowanceManager.syncState()
        val continuousBreakManager = com.outloop.prototype.continuous.ContinuousBreakManager.getInstance(this)
        continuousBreakManager.onDismissPromptRequested = null
        continuousBreakManager.resetSession("Service unbind")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopScheduledCheck("Service destroy")
        sessionExitHandler.removeCallbacks(sessionExitRunnable)
        hideAllOverlays("Service destroy")
        restrictionOverlay = null
        continuousBreakOverlay = null
        DiagnosticSessionRepository.setServiceConnected(false)
        com.outloop.prototype.reminder.ReelsReminderManager.syncState()
        val allowanceManager = com.outloop.prototype.allowance.ReelsAllowanceManager.getInstance(this)
        allowanceManager.onRestrictionDismissRequested = null
        allowanceManager.syncState()
        val continuousBreakManager = com.outloop.prototype.continuous.ContinuousBreakManager.getInstance(this)
        continuousBreakManager.onDismissPromptRequested = null
        continuousBreakManager.resetSession("Service destroy")
        super.onDestroy()
    }
}
