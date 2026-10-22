package com.outloop.prototype.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.outloop.prototype.continuous.ContinuousBreakConstants
import com.outloop.prototype.continuous.ContinuousBreakManager
import com.outloop.prototype.continuous.IntervalValidationResult
import com.outloop.prototype.continuous.validateCustomInterval
import com.outloop.prototype.ui.components.OutLoopButton
import com.outloop.prototype.ui.components.OutLoopButtonStyle
import com.outloop.prototype.ui.components.OutLoopCard
import com.outloop.prototype.ui.components.OutLoopFilterChip
import com.outloop.prototype.ui.components.OutLoopStatusBadge
import com.outloop.prototype.ui.theme.LabelMonoSmall
import com.outloop.prototype.ui.theme.OutLoopTheme

/**
 * Settings card for Continuous Reels Break.
 * Allows users to enable/disable proactive break reminders, select preset watching intervals,
 * or configure a custom interval (1..120 minutes) with strict validation.
 */
@Composable
fun ContinuousBreakCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manager = remember { ContinuousBreakManager.getInstance(context) }
    val colors = OutLoopTheme.colors

    val isEnabled by manager.isBreakReminderEnabled.collectAsState()
    val intervalMinutes by manager.breakIntervalMinutes.collectAsState()
    val continuousAccumulatedMs by manager.continuousAccumulatedMs.collectAsState()
    val lastClassificationState by manager.lastClassificationState.collectAsState()
    val lastOverlayStatus by manager.lastOverlayStatus.collectAsState()
    val isServiceConnected by com.outloop.prototype.repository.DiagnosticSessionRepository.isServiceConnected.collectAsState()

    var showCustomDialog by remember { mutableStateOf(false) }

    OutLoopCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = colors.surfaceCard,
        borderColor = colors.surfaceBorder,
        shape = RoundedCornerShape(20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.surfaceContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = "Continuous Break",
                            tint = if (isEnabled) colors.accentLime else colors.textMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Continuous Reels Break",
                            style = OutLoopTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mindful check-in during long sessions",
                            style = OutLoopTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }

                OutLoopStatusBadge(
                    text = if (isEnabled) "ON" else "OFF",
                    containerColor = if (isEnabled) colors.accentLime.copy(alpha = 0.15f) else colors.surfaceBorder,
                    contentColor = if (isEnabled) colors.accentLime else colors.textMuted,
                    pulse = isEnabled
                )
            }

            // Explanatory copy
            Text(
                text = "Prompts a gentle check-in after continuous watching so you can decide whether to step away or continue mindfully. Dismissing does not affect your daily allowance.",
                style = OutLoopTheme.typography.bodyMedium,
                color = colors.textSecondary,
                lineHeight = 20.sp
            )

            // Master Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceContainer, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Break Reminders",
                        style = OutLoopTheme.typography.labelLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Prompt a 30s reset after continuous watching",
                        style = OutLoopTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        manager.setBreakReminderEnabled(checked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onAccentLime,
                        checkedTrackColor = colors.accentLime,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.surfaceBorder
                    )
                )
            }

            // Configurable Intervals Section
            AnimatedVisibility(visible = isEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Watching Interval Before Break",
                            style = OutLoopTheme.typography.labelMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Every ${intervalMinutes}m",
                            style = LabelMonoSmall,
                            color = colors.accentLime,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Presets Row (2m, 3m, 4m, 5m, 10m, 15m) + Custom
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ContinuousBreakConstants.PRESET_INTERVALS.forEach { mins ->
                            val isSelected = intervalMinutes == mins
                            OutLoopFilterChip(
                                label = "${mins}m",
                                selected = isSelected,
                                onClick = { manager.setBreakIntervalMinutes(mins) }
                            )
                        }
                    }

                    // Custom Interval Action Row
                    val isCustomSelected = !ContinuousBreakConstants.PRESET_INTERVALS.contains(intervalMinutes)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isCustomSelected) "Custom interval: ${intervalMinutes} minutes" else "Need a different interval?",
                            style = OutLoopTheme.typography.bodySmall,
                            color = if (isCustomSelected) colors.accentLime else colors.textSecondary
                        )
                        TextButton(onClick = { showCustomDialog = true }) {
                            Text(
                                text = if (isCustomSelected) "Change Custom" else "Set Custom",
                                color = colors.accentLime,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Duration Details Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Optional check-in: Prompts two simple choices ('Take a break' or 'Continue watching') right over Instagram without forced countdowns or screen redirects.",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textMuted,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // In-App Diagnostic Runtime Status Panel
                    var showDiagnostics by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceContainer, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (isServiceConnected) colors.accentLime else colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Break Runtime Diagnostics",
                                    style = OutLoopTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                            TextButton(
                                onClick = { showDiagnostics = !showDiagnostics },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = if (showDiagnostics) "Hide" else "Show",
                                    color = colors.accentLime,
                                    style = OutLoopTheme.typography.labelSmall
                                )
                            }
                        }

                        if (showDiagnostics) {
                            val elapsedSec = continuousAccumulatedMs / 1000L
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Break Reminders", style = OutLoopTheme.typography.bodySmall, color = colors.textSecondary)
                                    Text(if (isEnabled) "Enabled" else "Disabled", style = LabelMonoSmall, color = if (isEnabled) colors.accentLime else colors.textMuted)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Accessibility Service", style = OutLoopTheme.typography.bodySmall, color = colors.textSecondary)
                                    Text(if (isServiceConnected) "Connected" else "Disconnected", style = LabelMonoSmall, color = if (isServiceConnected) colors.accentLime else colors.statusWarning)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Break Interval", style = OutLoopTheme.typography.bodySmall, color = colors.textSecondary)
                                    Text("${intervalMinutes}m (${intervalMinutes * 60}s)", style = LabelMonoSmall, color = colors.textPrimary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Session Elapsed", style = OutLoopTheme.typography.bodySmall, color = colors.textSecondary)
                                    Text("${elapsedSec}s", style = LabelMonoSmall, color = colors.accentLime)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Last Classification", style = OutLoopTheme.typography.bodySmall, color = colors.textSecondary)
                                    Text(lastClassificationState, style = LabelMonoSmall, color = colors.textPrimary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Last Overlay Status", style = OutLoopTheme.typography.bodySmall, color = colors.textSecondary)
                                    Text(lastOverlayStatus, style = LabelMonoSmall, color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom Interval Dialog
    if (showCustomDialog) {
        var customInputText by remember { mutableStateOf(intervalMinutes.toString()) }
        val validationResult = validateCustomInterval(customInputText)
        val isInputValid = validationResult is IntervalValidationResult.Valid
        val errorMessage = if (validationResult is IntervalValidationResult.Invalid) {
            validationResult.errorMessage
        } else null

        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = {
                Text(
                    text = "Custom Break Interval",
                    style = OutLoopTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter how many continuous minutes of Reels watching should trigger a 30-second break prompt (1 to 120 minutes):",
                        style = OutLoopTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    OutlinedTextField(
                        value = customInputText,
                        onValueChange = { customInputText = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = !isInputValid,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentLime,
                            unfocusedBorderColor = colors.surfaceBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            errorBorderColor = colors.statusWarning,
                            errorTextColor = colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = OutLoopTheme.typography.bodySmall,
                            color = colors.statusWarning
                        )
                    }
                }
            },
            confirmButton = {
                OutLoopButton(
                    text = "Apply",
                    onClick = {
                        if (validationResult is IntervalValidationResult.Valid) {
                            manager.setBreakIntervalMinutes(validationResult.minutes)
                            showCustomDialog = false
                        }
                    },
                    enabled = isInputValid,
                    style = OutLoopButtonStyle.PrimaryKinetic
                )
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text(text = "Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceCard,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
