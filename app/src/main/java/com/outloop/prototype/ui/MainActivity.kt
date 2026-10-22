package com.outloop.prototype.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.outloop.prototype.OutLoopAccessibilityService
import com.outloop.prototype.repository.DiagnosticSessionRepository
import com.outloop.prototype.repository.UserPreferencesRepository
import com.outloop.prototype.ui.navigation.AppScreen
import com.outloop.prototype.ui.navigation.OnboardingStep
import com.outloop.prototype.ui.screens.AccessibilitySetupScreen
import com.outloop.prototype.ui.screens.DashboardScreen
import com.outloop.prototype.ui.screens.HowItWorksScreen
import com.outloop.prototype.ui.screens.PrivacyCharterScreen
import com.outloop.prototype.ui.screens.WelcomeScreen
import com.outloop.prototype.ui.theme.OutLoopTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_START_SCROLL_RESCUE_RESET = "com.outloop.extra.START_SCROLL_RESCUE_RESET"
    }

    private var isServiceEnabledInSettings by mutableStateOf(false)
    private var showScrollRescueReset by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UserPreferencesRepository.init(this)
        checkAccessibilityServiceEnabled()
        com.outloop.prototype.reminder.ReelsReminderManager.ensureNotificationChannel(this)

        if (intent?.getBooleanExtra(EXTRA_START_SCROLL_RESCUE_RESET, false) == true) {
            showScrollRescueReset = true
        }

        setContent {
            OutLoopTheme {
                val isOnboardingCompleted by UserPreferencesRepository.isOnboardingCompleted.collectAsState()
                var onboardingStep by remember { mutableStateOf<OnboardingStep>(OnboardingStep.Welcome) }
                var currentAppScreen by remember { mutableStateOf<AppScreen>(AppScreen.Dashboard) }

                val isServiceConnected by DiagnosticSessionRepository.isServiceConnected.collectAsState()
                val isSessionActive by DiagnosticSessionRepository.isSessionActive.collectAsState()
                val diagnosticResults by DiagnosticSessionRepository.diagnosticResults.collectAsState()
                val totalRecordedCount by DiagnosticSessionRepository.totalRecordedCount.collectAsState()

                if (showScrollRescueReset) {
                    ScrollRescueResetDialog(
                        onDismiss = { showScrollRescueReset = false }
                    )
                }

                if (!isOnboardingCompleted) {
                    when (onboardingStep) {
                        is OnboardingStep.Welcome -> {
                            WelcomeScreen(
                                onGetStarted = { onboardingStep = OnboardingStep.HowItWorks }
                            )
                        }
                        is OnboardingStep.HowItWorks -> {
                            BackHandler { onboardingStep = OnboardingStep.Welcome }
                            HowItWorksScreen(
                                onContinue = { onboardingStep = OnboardingStep.PrivacyExplanation },
                                onLearnAboutPrivacy = { onboardingStep = OnboardingStep.PrivacyExplanation },
                                onBack = { onboardingStep = OnboardingStep.Welcome }
                            )
                        }
                        is OnboardingStep.PrivacyExplanation -> {
                            BackHandler { onboardingStep = OnboardingStep.HowItWorks }
                            PrivacyCharterScreen(
                                onContinue = { onboardingStep = OnboardingStep.AccessibilitySetup },
                                onNotNow = {
                                    UserPreferencesRepository.setOnboardingCompleted(this@MainActivity, true)
                                },
                                onBack = { onboardingStep = OnboardingStep.HowItWorks }
                            )
                        }
                        is OnboardingStep.AccessibilitySetup -> {
                            BackHandler { onboardingStep = OnboardingStep.PrivacyExplanation }
                            AccessibilitySetupScreen(
                                isServiceEnabledInSettings = isServiceEnabledInSettings,
                                isServiceConnected = isServiceConnected,
                                onOpenAccessibilitySettings = { openAccessibilitySettings() },
                                onFinish = {
                                    UserPreferencesRepository.setOnboardingCompleted(this@MainActivity, true)
                                },
                                onBack = { onboardingStep = OnboardingStep.PrivacyExplanation }
                            )
                        }
                    }
                } else {
                    when (currentAppScreen) {
                        is AppScreen.Dashboard -> {
                            DashboardScreen(
                                isServiceEnabledInSettings = isServiceEnabledInSettings,
                                isServiceConnected = isServiceConnected,
                                isSessionActive = isSessionActive,
                                onStartSession = { DiagnosticSessionRepository.startSession() },
                                onStopSession = { DiagnosticSessionRepository.stopSession() },
                                onViewLiveResults = { currentAppScreen = AppScreen.LiveResults },
                                onOpenSetup = { currentAppScreen = AppScreen.AccessibilitySetup },
                                onStartScrollRescueReset = { showScrollRescueReset = true }
                            )
                        }
                        is AppScreen.LiveResults -> {
                            BackHandler { currentAppScreen = AppScreen.Dashboard }
                            DiagnosticsScreen(
                                isServiceEnabledInSettings = isServiceEnabledInSettings,
                                isServiceConnected = isServiceConnected,
                                isSessionActive = isSessionActive,
                                diagnosticResults = diagnosticResults,
                                totalRecordedCount = totalRecordedCount,
                                onStartSession = { DiagnosticSessionRepository.startSession() },
                                onStopSession = { DiagnosticSessionRepository.stopSession() },
                                onClearResults = { DiagnosticSessionRepository.clearResults() },
                                onOpenAccessibilitySettings = { currentAppScreen = AppScreen.AccessibilitySetup },
                                onBack = { currentAppScreen = AppScreen.Dashboard }
                            )
                        }
                        is AppScreen.AccessibilitySetup -> {
                            BackHandler { currentAppScreen = AppScreen.Dashboard }
                            AccessibilitySetupScreen(
                                isServiceEnabledInSettings = isServiceEnabledInSettings,
                                isServiceConnected = isServiceConnected,
                                onOpenAccessibilitySettings = { openAccessibilitySettings() },
                                onFinish = { currentAppScreen = AppScreen.Dashboard },
                                onBack = { currentAppScreen = AppScreen.Dashboard }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START_SCROLL_RESCUE_RESET, false)) {
            showScrollRescueReset = true
        }
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityServiceEnabled()
        com.outloop.prototype.reminder.ReelsReminderManager.syncState()
    }

    private fun checkAccessibilityServiceEnabled() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val expectedServiceName = "${packageName}/${OutLoopAccessibilityService::class.java.canonicalName}"

        isServiceEnabledInSettings = enabledServices?.any { service ->
            val id = service.id
            id.equals(expectedServiceName, ignoreCase = true) ||
                    id.contains(OutLoopAccessibilityService::class.java.simpleName)
        } == true
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }
}
