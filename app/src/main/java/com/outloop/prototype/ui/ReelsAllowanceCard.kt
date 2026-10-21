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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.outloop.prototype.allowance.ReelsAllowanceManager
import com.outloop.prototype.ui.components.OutLoopCard
import com.outloop.prototype.ui.components.OutLoopFilterChip
import com.outloop.prototype.ui.components.OutLoopStatusBadge
import com.outloop.prototype.ui.theme.LabelMonoMedium
import com.outloop.prototype.ui.theme.LabelMonoSmall
import com.outloop.prototype.ui.theme.OutLoopTheme

/**
 * Focus dashboard card configuring and displaying OutLoop's daily Reels allowance.
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
    val dailyAccumulatedMs by allowanceManager.dailyAccumulatedMs.collectAsState()
    val allowanceState by allowanceManager.allowanceState.collectAsState()

    val accumulatedSeconds = (dailyAccumulatedMs / 1000L) % 60
    val accumulatedMinutes = (dailyAccumulatedMs / (60 * 1000L)) % 60
    val accumulatedHours = dailyAccumulatedMs / (60 * 60 * 1000L)

    val formattedTime = if (accumulatedHours > 0) {
        String.format("%02d:%02d:%02d", accumulatedHours, accumulatedMinutes, accumulatedSeconds)
    } else {
        String.format("%02d:%02d", accumulatedMinutes, accumulatedSeconds)
    }

    val limitMs = dailyLimitMinutes * 60 * 1000L
    val progress = if (limitMs > 0L) {
        (dailyAccumulatedMs.toFloat() / limitMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

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
                        Text(
                            text = "Daily Allowance Limit",
                            style = OutLoopTheme.typography.labelMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(15L, 30L, 45L, 60L).forEach { mins ->
                                OutLoopFilterChip(
                                    label = "${mins}m",
                                    selected = dailyLimitMinutes == mins,
                                    onClick = { allowanceManager.setDailyLimitMinutes(mins) },
                                    modifier = Modifier.weight(1f)
                                )
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

                            Text(
                                text = "$formattedTime / ${dailyLimitMinutes}m",
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
                            Text(
                                text = if (allowanceState == ReelsAllowanceManager.AllowanceState.EXHAUSTED) "0m remaining" else "${remainingMins}m remaining",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                fontWeight = FontWeight.Medium
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
