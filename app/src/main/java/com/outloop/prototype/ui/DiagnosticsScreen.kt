package com.outloop.prototype.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.outloop.classifier.CandidateScreenState
import com.outloop.classifier.ClassificationResult
import com.outloop.classifier.Confidence
import com.outloop.prototype.R
import com.outloop.prototype.reporting.SanitizedReportExporter
import com.outloop.prototype.repository.DiagnosticSessionRepository
import com.outloop.prototype.ui.theme.AccentAmber
import com.outloop.prototype.ui.theme.AccentEmerald
import com.outloop.prototype.ui.theme.AccentRose
import com.outloop.prototype.ui.theme.DarkBackground
import com.outloop.prototype.ui.theme.PrimaryIndigo
import com.outloop.prototype.ui.theme.StateDm
import com.outloop.prototype.ui.theme.StateFeed
import com.outloop.prototype.ui.theme.StateProfile
import com.outloop.prototype.ui.theme.StateReels
import com.outloop.prototype.ui.theme.StateStories
import com.outloop.prototype.ui.theme.StateUnknown
import com.outloop.prototype.ui.theme.StateVideo
import com.outloop.prototype.ui.theme.SurfaceDark
import com.outloop.prototype.ui.theme.SurfaceLightDark
import com.outloop.prototype.ui.theme.TextMuted
import com.outloop.prototype.ui.theme.TextPrimary
import com.outloop.prototype.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    isServiceEnabledInSettings: Boolean,
    isServiceConnected: Boolean,
    isSessionActive: Boolean,
    diagnosticResults: List<ClassificationResult>,
    totalRecordedCount: Int = diagnosticResults.size,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onClearResults: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var showConsentDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                HeaderSection(onBack = onBack)
            }

            item(key = "experiment_overview") {
                ExperimentOverviewCard()
            }

            item(key = "status_panel") {
                StatusPanel(
                    isServiceEnabled = isServiceEnabledInSettings,
                    isServiceConnected = isServiceConnected,
                    isSessionActive = isSessionActive,
                    onRequestEnableService = { showConsentDialog = true }
                )
            }

            item(key = "diagnostic_controls") {
                DiagnosticControlsCard(
                    isServiceConnected = isServiceConnected,
                    isSessionActive = isSessionActive,
                    hasResults = diagnosticResults.isNotEmpty(),
                    results = diagnosticResults,
                    totalRecordedCount = totalRecordedCount,
                    onStartSession = onStartSession,
                    onStopSession = onStopSession,
                    onClearResults = onClearResults
                )
            }

            item(key = "reminder_card") {
                ReminderSettingsCard()
            }

            item(key = "results_header") {
                ResultsHeader(
                    resultsCount = diagnosticResults.size,
                    totalRecordedCount = totalRecordedCount,
                    isSessionActive = isSessionActive
                )
            }

            if (diagnosticResults.isEmpty()) {
                item(key = "empty_results") {
                    EmptyResultsView(isSessionActive)
                }
            } else {
                items(
                    items = diagnosticResults,
                    key = { "${it.timestamp}_${it.hashCode()}" }
                ) { resultItem ->
                    ResultItemCard(resultItem)
                }
            }
        }
    }

    if (showConsentDialog) {
        AccessibilityConsentDialog(
            onConfirm = {
                showConsentDialog = false
                onOpenAccessibilitySettings()
            },
            onDismiss = { showConsentDialog = false }
        )
    }
}

@Composable
private fun HeaderSection(onBack: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onBack != null) {
                androidx.compose.material3.IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .background(SurfaceLightDark, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(28.dp)
                        .background(PrimaryIndigo, RoundedCornerShape(4.dp))
                )
            }
            Text(
                text = stringResource(id = R.string.app_name),
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color = SurfaceLightDark,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "PROTOTYPE",
                    color = AccentEmerald,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = stringResource(id = R.string.app_tagline),
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = if (onBack != null) 44.dp else 18.dp, top = 2.dp)
        )
    }
}

@Composable
private fun ExperimentOverviewCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = PrimaryIndigo,
                modifier = Modifier.padding(top = 2.dp)
            )
            Column {
                Text(
                    text = stringResource(id = R.string.diagnostic_experiment_title),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.diagnostic_experiment_summary),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun StatusPanel(
    isServiceEnabled: Boolean,
    isServiceConnected: Boolean,
    isSessionActive: Boolean,
    onRequestEnableService: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "System Status",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            StatusRow(
                label = "Android Settings Service",
                statusText = if (isServiceEnabled) "Enabled" else "Disabled",
                isPositive = isServiceEnabled
            )

            Spacer(modifier = Modifier.height(6.dp))

            StatusRow(
                label = "Accessibility Connection",
                statusText = if (isServiceConnected) "Connected" else "Disconnected",
                isPositive = isServiceConnected
            )

            Spacer(modifier = Modifier.height(6.dp))

            StatusRow(
                label = "Diagnostic Session",
                statusText = if (isSessionActive) "RUNNING" else "STOPPED",
                isPositive = isSessionActive,
                highlight = true
            )

            // Warning if service disconnected
            AnimatedVisibility(visible = !isServiceConnected) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AccentAmber,
                            modifier = Modifier.height(16.dp)
                        )
                        Text(
                            text = "Reliable detection is not active until service is enabled and connected.",
                            color = AccentAmber,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRequestEnableService,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Configure in Android Settings", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    statusText: String,
    isPositive: Boolean,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextPrimary, fontSize = 13.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(8.dp)
                    .background(
                        if (isPositive) AccentEmerald else if (highlight) AccentAmber else AccentRose,
                        CircleShape
                    )
            )
            Text(
                text = statusText,
                color = if (isPositive) AccentEmerald else if (highlight) AccentAmber else AccentRose,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun generateSanitizedReport(
    results: List<ClassificationResult>,
    totalRecordedCount: Int = results.size,
    isSessionActive: Boolean = false
): String {
    return SanitizedReportExporter.generateSanitizedReport(
        results = results,
        totalRecordedCount = totalRecordedCount,
        maxCapacity = DiagnosticSessionRepository.MAX_STORED_RESULTS,
        isSessionActive = isSessionActive
    ).reportText
}

private fun generateStructuralTreeReport(
    results: List<ClassificationResult>,
    totalRecordedCount: Int = results.size,
    isSessionActive: Boolean = false
): String {
    return SanitizedReportExporter.generateStructuralTreeReport(
        results = results,
        totalRecordedCount = totalRecordedCount,
        maxCapacity = DiagnosticSessionRepository.MAX_STORED_RESULTS,
        isSessionActive = isSessionActive
    ).reportText
}

@Composable
private fun DiagnosticControlsCard(
    isServiceConnected: Boolean,
    isSessionActive: Boolean,
    hasResults: Boolean,
    results: List<ClassificationResult> = emptyList(),
    totalRecordedCount: Int = results.size,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onClearResults: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && pendingExportText != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(pendingExportText!!.toByteArray(Charsets.UTF_8))
                }
                val byteCount = pendingExportText!!.toByteArray(Charsets.UTF_8).size
                val sizeKb = String.format(Locale.US, "%.1f KB", byteCount / 1024.0)
                statusMessage = "Report saved to file ($sizeKb)"
                statusIsError = false
            } catch (e: Exception) {
                statusMessage = "Failed to save file: ${e.message ?: "write error"}"
                statusIsError = true
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Diagnostic Controls",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val badgeColor = if (isSessionActive) AccentEmerald else TextMuted
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isSessionActive) "LIVE SESSION" else "STOPPED",
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Primary Session Action Button (Start / Stop) + Clear Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isSessionActive) {
                    Button(
                        onClick = {
                            statusMessage = null
                            onStartSession()
                        },
                        enabled = isServiceConnected,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Start Diagnostic Session",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            statusMessage = null
                            onStopSession()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Stop Diagnostic Session",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (hasResults) {
                    OutlinedButton(
                        onClick = {
                            statusMessage = null
                            onClearResults()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Clear", fontSize = 12.sp)
                    }
                }
            }

            // Export Actions: Copy Sanitized Report, Save File, Copy Tree
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Copy Sanitized Report button - prominent and easy to find
                OutlinedButton(
                    onClick = {
                        val export = SanitizedReportExporter.generateSanitizedReport(
                            results = results,
                            totalRecordedCount = totalRecordedCount,
                            maxCapacity = DiagnosticSessionRepository.MAX_STORED_RESULTS,
                            isSessionActive = isSessionActive
                        )
                        if (export.sizeBytes > SanitizedReportExporter.SAFE_CLIPBOARD_BYTE_LIMIT) {
                            statusMessage = "Report is ${export.formattedSize} (too large for clipboard). Tap 'Save File'."
                            statusIsError = true
                        } else {
                            try {
                                clipboardManager.setText(AnnotatedString(export.reportText))
                                statusMessage = "Copied report (${export.eventCount} events, ${export.formattedSize})"
                                statusIsError = false
                            } catch (e: Exception) {
                                statusMessage = "Clipboard error: ${e.message ?: "system rejected payload"}. Tap 'Save File'."
                                statusIsError = true
                            }
                        }
                    },
                    enabled = hasResults,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Copy Sanitized Report",
                        tint = if (hasResults) PrimaryIndigo else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copy Sanitized Report",
                        color = if (hasResults) PrimaryIndigo else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Save to File button (SAF)
                OutlinedButton(
                    onClick = {
                        val export = SanitizedReportExporter.generateSanitizedReport(
                            results = results,
                            totalRecordedCount = totalRecordedCount,
                            maxCapacity = DiagnosticSessionRepository.MAX_STORED_RESULTS,
                            isSessionActive = isSessionActive
                        )
                        pendingExportText = export.reportText
                        val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        createDocumentLauncher.launch("outloop_report_$timeStr.txt")
                    },
                    enabled = hasResults,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(0.7f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Export Report to File",
                        tint = if (hasResults) AccentEmerald else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Save",
                        color = if (hasResults) AccentEmerald else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Copy Tree Dump button
                OutlinedButton(
                    onClick = {
                        val export = SanitizedReportExporter.generateStructuralTreeReport(
                            results = results,
                            totalRecordedCount = totalRecordedCount,
                            maxCapacity = DiagnosticSessionRepository.MAX_STORED_RESULTS,
                            isSessionActive = isSessionActive
                        )
                        if (export.eventCount == 0) {
                            statusMessage = "No tree dumps recorded in this session."
                            statusIsError = true
                        } else if (export.sizeBytes > SanitizedReportExporter.SAFE_CLIPBOARD_BYTE_LIMIT) {
                            statusMessage = "Tree dump (${export.formattedSize}) exceeds clipboard. Tap 'Save File'."
                            statusIsError = true
                        } else {
                            try {
                                clipboardManager.setText(AnnotatedString(export.reportText))
                                statusMessage = "Copied tree dump (${export.eventCount} trees, ${export.formattedSize})"
                                statusIsError = false
                            } catch (e: Exception) {
                                statusMessage = "Clipboard error: ${e.message ?: "system rejected payload"}."
                                statusIsError = true
                            }
                        }
                    },
                    enabled = hasResults,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Copy Structural Tree Report",
                        tint = if (hasResults) PrimaryIndigo else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Tree",
                        color = if (hasResults) PrimaryIndigo else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Status feedback banner
            if (statusMessage != null) {
                Surface(
                    color = if (statusIsError) AccentAmber.copy(alpha = 0.15f) else AccentEmerald.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusMessage!!,
                        color = if (statusIsError) AccentAmber else AccentEmerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsHeader(
    resultsCount: Int,
    totalRecordedCount: Int = resultsCount,
    isSessionActive: Boolean
) {
    val droppedCount = maxOf(0, totalRecordedCount - resultsCount)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sanitized Detection Events ($resultsCount)",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (isSessionActive) {
                Text(
                    text = "● Live Recording",
                    color = AccentEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (droppedCount > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = AccentAmber.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Capacity warning: Showing newest $resultsCount of $totalRecordedCount events ($droppedCount evicted). Complete export is available.",
                    color = AccentAmber,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyResultsView(isSessionActive: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isSessionActive)
                    "Session is active.\nOpen Instagram and navigate between tabs to capture layout events."
                else
                    "Diagnostics are stopped.\nTap 'Start Diagnostic Session' above to begin capturing sanitized detection events.",
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ResultItemCard(result: ClassificationResult) {
    val stateColor = when (result.candidateState) {
        CandidateScreenState.REELS_TAB -> StateReels
        CandidateScreenState.DM_INBOX, CandidateScreenState.DM_CONVERSATION -> StateDm
        CandidateScreenState.STORIES -> StateStories
        CandidateScreenState.HOME_FEED -> StateFeed
        CandidateScreenState.PROFILE -> StateProfile
        CandidateScreenState.FULL_SCREEN_VIDEO -> StateVideo
        CandidateScreenState.UNKNOWN -> StateUnknown
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val formattedTime = remember(result.timestamp) { timeFormatter.format(Date(result.timestamp)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // State Badge
                Surface(
                    color = stateColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = result.candidateState.displayName,
                        color = stateColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = formattedTime,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reason and Confidence Tags
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = SurfaceLightDark,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = result.reasonCode.name,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                val confColor = when (result.confidence) {
                    Confidence.HIGH -> AccentEmerald
                    Confidence.MEDIUM -> AccentAmber
                    Confidence.LOW -> TextMuted
                }
                Surface(
                    color = confColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = result.confidence.label,
                        color = confColor,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = if (result.isDeviceVerified) AccentEmerald.copy(alpha = 0.15f) else SurfaceLightDark,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (result.isDeviceVerified) "Device-Verified" else "Synthetic / Unverified",
                        color = if (result.isDeviceVerified) AccentEmerald else TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Sanitized Explanation
            Text(
                text = result.evidenceExplanation,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            // Privacy-Safe Structural Diagnostic Summary
            result.diagnosticSummary?.let { diag ->
                Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = SurfaceLightDark.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = diag.eventType ?: "UNKNOWN_EVENT",
                                    color = PrimaryIndigo,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Nodes: ${diag.nodesVisited} | Depth: ${diag.maxDepthReached}",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Outcome: ${diag.observationOutcome.name}",
                                    color = when (diag.observationOutcome) {
                                        com.outloop.classifier.ObservationOutcome.CLASSIFIED -> AccentEmerald
                                        com.outloop.classifier.ObservationOutcome.PARTIAL_OR_TRUNCATED -> AccentAmber
                                        com.outloop.classifier.ObservationOutcome.ROOT_UNAVAILABLE -> AccentRose
                                        com.outloop.classifier.ObservationOutcome.NO_MATCHING_SIGNALS -> TextMuted
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Root: ${diag.rootAcquisitionMethod}",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }

                            if (diag.isPartialTree) {
                                Text(
                                    text = "⚠ Partial event-source subtree (window root was unavailable)",
                                    color = AccentAmber,
                                    fontSize = 10.sp
                                )
                            }

                            if (diag.reachedNodeLimit || diag.reachedDepthLimit) {
                                Text(
                                    text = "⚠ Traversal limit reached: ${if (diag.reachedNodeLimit) "node limit (250)" else "depth limit (14)"}",
                                    color = AccentAmber,
                                    fontSize = 10.sp
                                )
                            }

                            val navDesc = if (diag.bottomNavObservable) {
                                val sel = diag.selectedBottomTabOrdinal?.let { "Tab #$it" } ?: "No tab selected"
                                "Observed (${diag.bottomNavChildCount ?: "?"} tabs, $sel)"
                            } else {
                                "Not observed"
                            }
                            Text(
                                text = "Bottom Nav: $navDesc",
                                color = if (diag.bottomNavObservable) AccentEmerald else TextMuted,
                                fontSize = 10.sp
                            )

                            if (diag.activeStructuralSignals.isNotEmpty()) {
                                Text(
                                    text = "Signals: ${diag.activeStructuralSignals.joinToString(", ")}",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }

                            if (diag.observedClassCounts.isNotEmpty()) {
                                val topClasses = diag.observedClassCounts.entries
                                    .sortedByDescending { it.value }
                                    .take(4)
                                    .joinToString(", ") { "${it.key}: ${it.value}" }
                                Text(
                                    text = "Classes: $topClasses",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

            }
        }
    }
}

@Composable
private fun AccessibilityConsentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryIndigo)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.consent_dialog_title), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Text(
                text = stringResource(id = R.string.consent_dialog_body),
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
            ) {
                Text(text = stringResource(id = R.string.btn_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.btn_cancel), color = TextMuted)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(14.dp)
    )
}
