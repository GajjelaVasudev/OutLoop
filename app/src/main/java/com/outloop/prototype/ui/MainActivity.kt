package com.outloop.prototype.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.outloop.prototype.OutLoopAccessibilityService
import com.outloop.prototype.repository.DiagnosticSessionRepository
import com.outloop.prototype.ui.theme.OutLoopTheme

class MainActivity : ComponentActivity() {

    private var isServiceEnabledInSettings by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAccessibilityServiceEnabled()

        setContent {
            OutLoopTheme {
                val isServiceConnected by DiagnosticSessionRepository.isServiceConnected.collectAsState()
                val isSessionActive by DiagnosticSessionRepository.isSessionActive.collectAsState()
                val diagnosticResults by DiagnosticSessionRepository.diagnosticResults.collectAsState()
                val totalRecordedCount by DiagnosticSessionRepository.totalRecordedCount.collectAsState()

                DiagnosticsScreen(
                    isServiceEnabledInSettings = isServiceEnabledInSettings,
                    isServiceConnected = isServiceConnected,
                    isSessionActive = isSessionActive,
                    diagnosticResults = diagnosticResults,
                    totalRecordedCount = totalRecordedCount,
                    onStartSession = { DiagnosticSessionRepository.startSession() },
                    onStopSession = { DiagnosticSessionRepository.stopSession() },
                    onClearResults = { DiagnosticSessionRepository.clearResults() },
                    onOpenAccessibilitySettings = { openAccessibilitySettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityServiceEnabled()
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
