package com.outloop.prototype.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.outloop.prototype.allowance.DailyAllowanceConstants
import com.outloop.prototype.allowance.DailyLimitValidationResult
import com.outloop.prototype.allowance.ReelsAllowanceManager
import com.outloop.prototype.allowance.SpecialDayPresets
import com.outloop.prototype.allowance.SpecialDayReasons
import com.outloop.prototype.allowance.validateCustomDailyLimit
import com.outloop.prototype.ui.components.OutLoopButton
import com.outloop.prototype.ui.components.OutLoopButtonStyle
import com.outloop.prototype.ui.components.OutLoopCard
import com.outloop.prototype.ui.components.OutLoopFilterChip
import com.outloop.prototype.ui.components.OutLoopStatusBadge
import com.outloop.prototype.ui.theme.LabelMonoMedium
import com.outloop.prototype.ui.theme.LabelMonoSmall
import com.outloop.prototype.ui.theme.OutLoopTheme

/**
 * Focus dashboard card configuring and displaying OutLoop's daily Reels allowance
 * and Special-Day Flexibility (extra allowance and temporary restriction relaxation).
 */
@Composable
fun ReelsAllowanceCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val allowanceManager = ReelsAllowanceManager.getInstance(context)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowanceManager.syncState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        allowanceManager.syncState()
    }

    val colors = OutLoopTheme.colors
    val isEnabled by allowanceManager.isAllowanceEnabled.collectAsState()
    val dailyLimitMinutes by allowanceManager.dailyLimitMinutes.collectAsState()
    val extraAllowanceMinutes by allowanceManager.extraAllowanceMinutes.collectAsState()
    val effectiveLimitMinutes by allowanceManager.effectiveLimitMinutes.collectAsState()
    val isRestrictionRelaxed by allowanceManager.isRestrictionRelaxed.collectAsState()
    val specialDayReason by allowanceManager.specialDayReason.collectAsState()
    val dailyAccumulatedMs by allowanceManager.dailyAccumulatedMs.collectAsState()
    val allowanceState by allowanceManager.allowanceState.collectAsState()
    val isClockDiscrepancyDetected by allowanceManager.isClockDiscrepancyDetected.collectAsState()
    val extraHistory by allowanceManager.extraAllowanceHistory.collectAsState()

    var showExtraTimeDialog by remember { mutableStateOf(false) }
    var showRelaxDialog by remember { mutableStateOf(false) }
    var showCustomLimitDialog by remember { mutableStateOf(false) }
    var showRemoveAllExtraConfirmDialog by remember { mutableStateOf(false) }

    val accumulatedSeconds = (dailyAccumulatedMs / 1000L) % 60
    val accumulatedMinutes = (dailyAccumulatedMs / (60 * 1000L)) % 60
    val accumulatedHours = dailyAccumulatedMs / (60 * 60 * 1000L)

    val formattedTime = if (accumulatedHours > 0) {
        String.format("%02d:%02d:%02d", accumulatedHours, accumulatedMinutes, accumulatedSeconds)
    } else {
        String.format("%02d:%02d", accumulatedMinutes, accumulatedSeconds)
    }

    val effectiveLimitMs = effectiveLimitMinutes * 60 * 1000L
    val progress = if (effectiveLimitMs > 0L) {
        (dailyAccumulatedMs.toFloat() / effectiveLimitMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    if (showExtraTimeDialog) {
        ExtraTimeDialog(
            currentNormalLimit = dailyLimitMinutes,
            currentExtraMinutes = extraAllowanceMinutes,
            onDismiss = { showExtraTimeDialog = false },
            onConfirm = { mins, reason ->
                allowanceManager.addExtraAllowance(mins, reason)
                showExtraTimeDialog = false
            }
        )
    }

    if (showRelaxDialog) {
        RelaxRestrictionDialog(
            onDismiss = { showRelaxDialog = false },
            onConfirm = { reason ->
                allowanceManager.relaxRestrictionForToday(reason)
                showRelaxDialog = false
            }
        )
    }

    if (showCustomLimitDialog) {
        CustomDailyLimitDialog(
            currentLimitMinutes = dailyLimitMinutes,
            onDismiss = { showCustomLimitDialog = false },
            onConfirm = { mins ->
                allowanceManager.setDailyLimitMinutes(mins)
                showCustomLimitDialog = false
            }
        )
    }

    if (showRemoveAllExtraConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveAllExtraConfirmDialog = false },
            title = {
                Text(
                    text = "Remove All Extra Time?",
                    style = OutLoopTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will remove all +${extraAllowanceMinutes}m of active extra allowance and restore your daily limit to ${dailyLimitMinutes}m.\n\nYour accumulated usage today ($formattedTime) will NOT be reset. If your usage already reaches or exceeds ${dailyLimitMinutes}m, Reels restrictions will activate immediately.",
                    style = OutLoopTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        allowanceManager.removeAllExtraAllowance()
                        showRemoveAllExtraConfirmDialog = false
                    }
                ) {
                    Text(
                        text = "Remove All",
                        color = colors.statusWarning,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveAllExtraConfirmDialog = false }) {
                    Text(text = "Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceCard,
            shape = RoundedCornerShape(20.dp)
        )
    }

    OutLoopCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = colors.surfaceCard,
        borderColor = colors.surfaceBorder,
        shape = RoundedCornerShape(20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Clock Discrepancy Warning Banner
            AnimatedVisibility(visible = isClockDiscrepancyDetected) {
                Surface(
                    color = colors.statusWarning.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, colors.statusWarning.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colors.statusWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "System Clock Discrepancy Detected",
                                style = OutLoopTheme.typography.labelMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "OutLoop detected an unexpected change in device time. Daily resets are temporarily paused to protect restriction integrity. Normal operation will resume once device time aligns.",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
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
                            imageVector = Icons.Default.LockClock,
                            contentDescription = null,
                            tint = if (isEnabled) colors.accentLime else colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Daily Reels Allowance",
                            style = OutLoopTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Enforces mindful daily limit on Reels",
                            style = OutLoopTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }

                // Status Badge
                val (badgeText, badgeColor, isPulse) = when (allowanceState) {
                    ReelsAllowanceManager.AllowanceState.DISABLED -> Triple("OFF", colors.textMuted, false)
                    ReelsAllowanceManager.AllowanceState.RELAXED -> Triple("RELAXED TODAY", colors.accentLime, false)
                    ReelsAllowanceManager.AllowanceState.EXHAUSTED -> Triple("LIMIT REACHED", colors.statusWarning, true)
                    ReelsAllowanceManager.AllowanceState.TRACKING_WITHIN_ALLOWANCE -> Triple("TRACKING", colors.accentLime, true)
                    ReelsAllowanceManager.AllowanceState.PAUSED_WITHIN_ALLOWANCE -> Triple("ACTIVE", colors.accentLime, false)
                }

                OutLoopStatusBadge(
                    text = badgeText,
                    containerColor = badgeColor.copy(alpha = 0.15f),
                    contentColor = badgeColor,
                    pulse = isPulse
                )
            }

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
                        text = "Enable Daily Allowance",
                        style = OutLoopTheme.typography.labelLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Restrict Reels after allowance is exhausted",
                        style = OutLoopTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        allowanceManager.setAllowanceEnabled(checked)
                        if (checked && dailyLimitMinutes == 0L) {
                            allowanceManager.setDailyLimitMinutes(15L) // default to 15m preset
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onAccentLime,
                        checkedTrackColor = colors.accentLime,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.surfaceBorder
                    )
                )
            }

            AnimatedVisibility(visible = isEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Limit Presets Selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daily Allowance Limit",
                                style = OutLoopTheme.typography.labelMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            val isCustomSelected = !SpecialDayPresets.NORMAL_LIMIT_PRESETS.contains(dailyLimitMinutes)
                            Text(
                                text = if (extraAllowanceMinutes > 0L) {
                                    "Normal: ${dailyLimitMinutes}m (+${extraAllowanceMinutes}m extra)"
                                } else {
                                    "${dailyLimitMinutes}m / day"
                                },
                                style = LabelMonoSmall,
                                color = if (isCustomSelected) colors.accentLime else colors.textSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val isCustomSelected = !SpecialDayPresets.NORMAL_LIMIT_PRESETS.contains(dailyLimitMinutes)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SpecialDayPresets.NORMAL_LIMIT_PRESETS.forEach { mins ->
                                OutLoopFilterChip(
                                    label = "${mins}m",
                                    selected = dailyLimitMinutes == mins,
                                    onClick = { allowanceManager.setDailyLimitMinutes(mins) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutLoopFilterChip(
                                label = if (isCustomSelected) "${dailyLimitMinutes}m" else "Custom",
                                selected = isCustomSelected,
                                onClick = { showCustomLimitDialog = true },
                                modifier = Modifier.weight(1.2f)
                            )
                        }

                        // Custom Daily Limit Action Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isCustomSelected) "Custom daily limit: ${dailyLimitMinutes} minutes" else "Need a custom limit?",
                                style = OutLoopTheme.typography.bodySmall,
                                color = if (isCustomSelected) colors.accentLime else colors.textSecondary
                            )
                            TextButton(onClick = { showCustomLimitDialog = true }) {
                                Text(
                                    text = if (isCustomSelected) "Change Custom" else "Set Custom",
                                    color = colors.accentLime,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Temporary Relaxation Banner (if active)
                    if (isRestrictionRelaxed) {
                        Surface(
                            color = colors.accentLime.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, colors.accentLime.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Weekend,
                                        contentDescription = null,
                                        tint = colors.accentLime,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Restrictions relaxed for today",
                                        style = OutLoopTheme.typography.labelLarge,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Normal Reels restrictions resume tomorrow at midnight. Your usage history is preserved.",
                                    style = OutLoopTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                                if (!specialDayReason.isNullOrBlank()) {
                                    Text(
                                        text = "Reason: $specialDayReason",
                                        style = OutLoopTheme.typography.labelSmall,
                                        color = colors.accentLime
                                    )
                                }
                                OutlinedButton(
                                    onClick = { allowanceManager.resumeNormalRestrictions() },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Resume Restrictions Now",
                                        color = colors.textPrimary,
                                        style = OutLoopTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }

                    // Progress Section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    imageVector = Icons.Default.HourglassBottom,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "TODAY'S USAGE",
                                    style = LabelMonoSmall,
                                    color = colors.textSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val allowanceDisplay = if (isRestrictionRelaxed) {
                                "$formattedTime used (relaxed)"
                            } else if (extraAllowanceMinutes > 0L) {
                                "$formattedTime / ${effectiveLimitMinutes}m (+${extraAllowanceMinutes}m extra)"
                            } else {
                                "$formattedTime / ${effectiveLimitMinutes}m"
                            }

                            Text(
                                text = allowanceDisplay,
                                style = LabelMonoMedium,
                                color = if (allowanceState == ReelsAllowanceManager.AllowanceState.EXHAUSTED) colors.statusWarning else colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (allowanceState == ReelsAllowanceManager.AllowanceState.EXHAUSTED) colors.statusWarning else colors.accentLime,
                            trackColor = colors.surfaceContainer
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Resets at midnight",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textMuted
                            )

                            val remainingMins = (allowanceManager.allowanceRemainingMs / 60000L).toInt()
                            val remainingText = when {
                                isRestrictionRelaxed -> "Restrictions relaxed today"
                                allowanceState == ReelsAllowanceManager.AllowanceState.EXHAUSTED -> "0m remaining"
                                else -> "${remainingMins}m remaining"
                            }

                            Text(
                                text = remainingText,
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Special-Day Flexibility Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Special-Day Flexibility",
                                style = OutLoopTheme.typography.labelMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Resumes tomorrow",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textMuted
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showExtraTimeDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreTime,
                                    contentDescription = null,
                                    tint = colors.accentLime,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (extraAllowanceMinutes > 0L) "+More Time" else "Add Extra Time",
                                    color = colors.textPrimary,
                                    style = OutLoopTheme.typography.labelMedium
                                )
                            }

                            if (!isRestrictionRelaxed) {
                                OutlinedButton(
                                    onClick = { showRelaxDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Weekend,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Relax Today",
                                        color = colors.textPrimary,
                                        style = OutLoopTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        // Extra Allowance Removal Controls (Undo & Remove All)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val lastAddition = extraHistory.lastOrNull() ?: extraAllowanceMinutes
                            OutlinedButton(
                                onClick = { allowanceManager.undoLastExtraAllowance() },
                                enabled = extraAllowanceMinutes > 0L,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = null,
                                    tint = if (extraAllowanceMinutes > 0L) colors.textPrimary else colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (extraAllowanceMinutes > 0L) "Undo (+${lastAddition}m)" else "Undo Extra",
                                    color = if (extraAllowanceMinutes > 0L) colors.textPrimary else colors.textMuted,
                                    style = OutLoopTheme.typography.labelMedium
                                )
                            }

                            OutlinedButton(
                                onClick = { showRemoveAllExtraConfirmDialog = true },
                                enabled = extraAllowanceMinutes > 0L,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = if (extraAllowanceMinutes > 0L) colors.statusWarning else colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Remove All",
                                    color = if (extraAllowanceMinutes > 0L) colors.statusWarning else colors.textMuted,
                                    style = OutLoopTheme.typography.labelMedium
                                )
                            }
                        }

                        if (extraAllowanceMinutes > 0L && !isRestrictionRelaxed) {
                            Text(
                                text = "Active extra allowance: +${extraAllowanceMinutes}m. Effective limit today: ${effectiveLimitMinutes}m (${dailyLimitMinutes}m normal + ${extraAllowanceMinutes}m extra). Normal limit resumes tomorrow.",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.accentLime,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Reset Today Button
                    OutlinedButton(
                        onClick = { allowanceManager.resetDailyAccumulation() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Today",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset Today's Usage",
                            color = colors.textPrimary,
                            style = OutLoopTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Footnote regarding preserved surfaces
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(14.dp).padding(top = 2.dp)
                )
                Text(
                    text = "When allowance expires, a mindful OutLoop screen covers Reels. Direct Messages, Stories, Profiles, and Home Feed remain fully accessible.",
                    style = OutLoopTheme.typography.bodySmall,
                    color = colors.textMuted,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * Modal dialog for requesting extra Reels allowance (+10m, +15m, +30m) with an optional reason.
 */
@Composable
private fun ExtraTimeDialog(
    currentNormalLimit: Long,
    currentExtraMinutes: Long,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Long, reason: String?) -> Unit
) {
    var selectedMinutes by remember { mutableStateOf(15L) }
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var customReasonText by remember { mutableStateOf("") }
    var isCustomSelected by remember { mutableStateOf(false) }

    val colors = OutLoopTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Extra Reels Time",
                style = OutLoopTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Request additional Reels time for today. This applies only to the current calendar day and will not change your normal daily limit.",
                    style = OutLoopTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )

                // Extra minutes presets
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Extra Time Amount",
                        style = OutLoopTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpecialDayPresets.EXTRA_MINUTES_PRESETS.forEach { mins ->
                            OutLoopFilterChip(
                                label = "+${mins}m",
                                selected = selectedMinutes == mins,
                                onClick = { selectedMinutes = mins },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Summary preview
                Surface(
                    color = colors.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.surfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Resulting Allowance Today",
                            style = OutLoopTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        val resulting = currentNormalLimit + currentExtraMinutes + selectedMinutes
                        Text(
                            text = "$resulting minutes total (${currentNormalLimit}m normal + ${currentExtraMinutes + selectedMinutes}m extra)",
                            style = OutLoopTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentLime
                        )
                    }
                }

                // Optional Reason Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Reason (Optional)",
                        style = OutLoopTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Reasons are for your own reflection and never affect your allowance.",
                        style = OutLoopTheme.typography.bodySmall,
                        color = colors.textMuted
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SpecialDayReasons.PRESETS.forEach { preset ->
                            val isSelected = !isCustomSelected && selectedReason == preset
                            OutLoopFilterChip(
                                label = preset,
                                selected = isSelected,
                                onClick = {
                                    isCustomSelected = false
                                    selectedReason = if (isSelected) null else preset
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customReasonText,
                        onValueChange = {
                            customReasonText = it
                            if (it.isNotBlank()) {
                                isCustomSelected = true
                                selectedReason = null
                            }
                        },
                        label = { Text("Or enter custom reason") },
                        placeholder = { Text("e.g. Vacation, celebration...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentLime,
                            unfocusedBorderColor = colors.surfaceBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedLabelColor = colors.accentLime,
                            unfocusedLabelColor = colors.textSecondary,
                            focusedContainerColor = colors.surfaceContainer,
                            unfocusedContainerColor = colors.surfaceContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            OutLoopButton(
                text = "Add +${selectedMinutes}m",
                onClick = {
                    val finalReason = if (isCustomSelected && customReasonText.isNotBlank()) {
                        customReasonText.trim()
                    } else {
                        selectedReason
                    }
                    onConfirm(selectedMinutes, finalReason)
                },
                style = OutLoopButtonStyle.PrimaryKinetic,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Cancel",
                    color = colors.textSecondary,
                    style = OutLoopTheme.typography.labelMedium
                )
            }
        },
        containerColor = colors.surfaceCard,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Modal dialog for confirming temporary relaxation of Reels restrictions for today.
 */
@Composable
private fun RelaxRestrictionDialog(
    onDismiss: () -> Unit,
    onConfirm: (reason: String?) -> Unit
) {
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var customReasonText by remember { mutableStateOf("") }
    var isCustomSelected by remember { mutableStateOf(false) }

    val colors = OutLoopTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Relax Restrictions for Today?",
                style = OutLoopTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = colors.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.surfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "What relaxing means:",
                            style = OutLoopTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "• The normal Reels restriction will be suspended for the rest of today.",
                            style = OutLoopTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "• Your existing usage history will be safely preserved.",
                            style = OutLoopTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "• Normal restrictions will return automatically tomorrow at midnight.",
                            style = OutLoopTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "• Direct Messages, Stories, profiles, and Home Feed remain fully accessible.",
                            style = OutLoopTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }

                // Optional Reason Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Reason (Optional)",
                        style = OutLoopTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Reasons are for your own reflection and never affect eligibility.",
                        style = OutLoopTheme.typography.bodySmall,
                        color = colors.textMuted
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SpecialDayReasons.PRESETS.forEach { preset ->
                            val isSelected = !isCustomSelected && selectedReason == preset
                            OutLoopFilterChip(
                                label = preset,
                                selected = isSelected,
                                onClick = {
                                    isCustomSelected = false
                                    selectedReason = if (isSelected) null else preset
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customReasonText,
                        onValueChange = {
                            customReasonText = it
                            if (it.isNotBlank()) {
                                isCustomSelected = true
                                selectedReason = null
                            }
                        },
                        label = { Text("Or enter custom reason") },
                        placeholder = { Text("e.g. Festival, celebration, personal day...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentLime,
                            unfocusedBorderColor = colors.surfaceBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedLabelColor = colors.accentLime,
                            unfocusedLabelColor = colors.textSecondary,
                            focusedContainerColor = colors.surfaceContainer,
                            unfocusedContainerColor = colors.surfaceContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            OutLoopButton(
                text = "Confirm & Relax Restrictions",
                onClick = {
                    val finalReason = if (isCustomSelected && customReasonText.isNotBlank()) {
                        customReasonText.trim()
                    } else {
                        selectedReason
                    }
                    onConfirm(finalReason)
                },
                style = OutLoopButtonStyle.PrimaryKinetic,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Keep Restrictions Active",
                    color = colors.textSecondary,
                    style = OutLoopTheme.typography.labelMedium
                )
            }
        },
        containerColor = colors.surfaceCard,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Modal dialog for configuring a custom daily Reels allowance limit (1 to 1440 minutes).
 */
@Composable
private fun CustomDailyLimitDialog(
    currentLimitMinutes: Long,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Long) -> Unit
) {
    var inputText by remember {
        mutableStateOf(if (currentLimitMinutes > 0L) currentLimitMinutes.toString() else "15")
    }
    val validationResult = remember(inputText) {
        validateCustomDailyLimit(inputText)
    }
    val isValid = validationResult is DailyLimitValidationResult.Valid
    val errorMessage = (validationResult as? DailyLimitValidationResult.Invalid)?.errorMessage

    val colors = OutLoopTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Custom Daily Limit",
                style = OutLoopTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Enter a daily Reels allowance between 1 minute and 1440 minutes (24 hours). Short limits like 1, 2, or 3 minutes can be used to test restriction behavior quickly.",
                    style = OutLoopTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Daily limit (minutes)") },
                    placeholder = { Text("e.g. 1, 2, 45, 120...") },
                    isError = !isValid && inputText.isNotBlank(),
                    supportingText = {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                color = colors.statusWarning,
                                style = OutLoopTheme.typography.bodySmall
                            )
                        } else if (isValid) {
                            val mins = (validationResult as DailyLimitValidationResult.Valid).minutes
                            val hours = mins / 60
                            val remMins = mins % 60
                            val timeStr = if (hours > 0 && remMins > 0) {
                                "${hours}h ${remMins}m"
                            } else if (hours > 0) {
                                "${hours}h"
                            } else {
                                "${mins}m"
                            }
                            Text(
                                text = "Valid: $timeStr per day",
                                color = colors.accentLime,
                                style = OutLoopTheme.typography.bodySmall
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentLime,
                        unfocusedBorderColor = colors.surfaceBorder,
                        errorBorderColor = colors.statusWarning,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedLabelColor = colors.accentLime,
                        unfocusedLabelColor = colors.textSecondary,
                        errorLabelColor = colors.statusWarning,
                        focusedContainerColor = colors.surfaceContainer,
                        unfocusedContainerColor = colors.surfaceContainer,
                        errorContainerColor = colors.surfaceContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )

                // Quick presets row for testing
                Text(
                    text = "Quick Test Limits",
                    style = OutLoopTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1L, 2L, 3L, 90L, 120L).forEach { quickVal ->
                        OutLoopFilterChip(
                            label = "${quickVal}m",
                            selected = inputText.trim() == quickVal.toString(),
                            onClick = { inputText = quickVal.toString() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            OutLoopButton(
                text = "Apply Limit",
                onClick = {
                    if (validationResult is DailyLimitValidationResult.Valid) {
                        onConfirm(validationResult.minutes)
                    }
                },
                enabled = isValid,
                style = OutLoopButtonStyle.PrimaryKinetic,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Cancel",
                    color = colors.textSecondary,
                    style = OutLoopTheme.typography.labelMedium
                )
            }
        },
        containerColor = colors.surfaceCard,
        shape = RoundedCornerShape(20.dp)
    )
}

