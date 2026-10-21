package com.outloop.prototype.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.outloop.prototype.ui.ReminderSettingsCard
import com.outloop.prototype.ui.components.OutLoopButton
import com.outloop.prototype.ui.components.OutLoopButtonStyle
import com.outloop.prototype.ui.components.OutLoopCard
import com.outloop.prototype.ui.components.OutLoopStatusBadge
import com.outloop.prototype.ui.components.PulsingDot
import com.outloop.prototype.ui.theme.LabelMonoSmall
import com.outloop.prototype.ui.theme.OutLoopTheme

/**
 * Screen 4: Main Focus Dashboard (Stitch Reference: 4._main_dashboard)
 * Visual source of truth: stitch_reference/4._main_dashboard/ & _dark_mode/
 */
@Composable
fun DashboardScreen(
    isServiceEnabledInSettings: Boolean,
    isServiceConnected: Boolean,
    isSessionActive: Boolean,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onViewLiveResults: () -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OutLoopTheme.colors
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasRoot)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(colors.accentLime, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AllInclusive,
                            contentDescription = "OutLoop Logo",
                            tint = colors.onAccentLime,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "OutLoop",
                        style = OutLoopTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onOpenSetup,
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.surfaceContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Setup & Permissions",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Main Intent Headline & Status Pill
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.surfaceBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PulsingDot(color = colors.accentLime, size = 6.dp)
                        Text(
                            text = "PHASE 01 DIAGNOSTIC",
                            style = LabelMonoSmall,
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Take back your attention.",
                    style = OutLoopTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "We're testing a smarter way to avoid endless Reels without losing the connections that matter.",
                    style = OutLoopTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    lineHeight = 20.sp
                )
            }

            // Primary Diagnostic Engine Card ("Instagram detection")
            OutLoopCard(
                containerColor = colors.surfaceCard,
                borderColor = colors.surfaceBorder,
                shape = RoundedCornerShape(20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Header row
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
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = null,
                                    tint = if (isSessionActive) colors.accentLime else colors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Instagram detection",
                                    style = OutLoopTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Real-time accessibility parser",
                                    style = OutLoopTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        // Live status pill
                        val (statusText, statusColor, isPulsing) = when {
                            isSessionActive -> Triple("ACTIVE", colors.accentLime, true)
                            isServiceEnabledInSettings && isServiceConnected -> Triple("READY", colors.accentLime, true)
                            isServiceEnabledInSettings -> Triple("CONNECTING", colors.statusWarning, true)
                            else -> Triple("SERVICE OFF", colors.statusWarning, false)
                        }

                        OutLoopStatusBadge(
                            text = statusText,
                            containerColor = statusColor.copy(alpha = 0.15f),
                            contentColor = statusColor,
                            pulse = isPulsing
                        )
                    }

                    // Explanatory copy
                    val explanationText = when {
                        isSessionActive -> "Diagnostic monitoring active. Evaluating local UI hierarchy structures in real time."
                        isServiceEnabledInSettings -> "Accessibility service active. Ready to run local structural classification when you launch Instagram."
                        else -> "Accessibility service inactive. Tap below to enable OutLoop in Android settings."
                    }
                    Text(
                        text = explanationText,
                        style = OutLoopTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        lineHeight = 20.sp
                    )

                    // Action Button
                    if (isSessionActive) {
                        OutLoopButton(
                            text = "Stop Diagnostic Session",
                            onClick = onStopSession,
                            style = OutLoopButtonStyle.Secondary,
                            leadingIcon = Icons.Default.Stop
                        )
                    } else {
                        OutLoopButton(
                            text = if (isServiceEnabledInSettings) "Start Diagnostic Session" else "Set Up Accessibility Service",
                            onClick = {
                                if (isServiceEnabledInSettings) onStartSession() else onOpenSetup()
                            },
                            style = OutLoopButtonStyle.PrimaryKinetic,
                            leadingIcon = if (isServiceEnabledInSettings) Icons.Default.PlayArrow else Icons.Default.Settings
                        )
                    }

                    // Disclaimer footnote
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Detection experiment only. Instagram is not blocked.",
                            style = OutLoopTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                }
            }

            // Daily Reels Allowance & Selective Restriction Section
            com.outloop.prototype.ui.ReelsAllowanceCard()

            // Selective Reels Reminder Section (Working Prototype Integration)
            ReminderSettingsCard()

            // Live Results Link Row
            OutLoopCard(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onViewLiveResults),
                containerColor = colors.surfaceCard,
                borderColor = colors.surfaceBorder,
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(colors.surfaceContainer, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeveloperBoard,
                                contentDescription = null,
                                tint = colors.accentLime,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "View Live Results",
                                style = OutLoopTheme.typography.labelLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Inspect sample classified interface nodes",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Navigate to Live Results",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Intended Target Matrix: "What stays accessible"
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text(
                        text = "What stays accessible",
                        style = OutLoopTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Intended product goals for selective focus",
                        style = OutLoopTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                // 2x2 Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // DMs Card
                    AccessibleFeatureTile(
                        icon = Icons.Default.Forum,
                        title = "DMs",
                        subtitle = "Direct Messages",
                        modifier = Modifier.weight(1f)
                    )
                    // Stories Card
                    AccessibleFeatureTile(
                        icon = Icons.Default.HistoryEdu,
                        title = "Stories",
                        subtitle = "Friends updates",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Profiles Card
                    AccessibleFeatureTile(
                        icon = Icons.Default.AccountCircle,
                        title = "Profiles",
                        subtitle = "User headers & bio",
                        modifier = Modifier.weight(1f)
                    )
                    // Home feed Card
                    AccessibleFeatureTile(
                        icon = Icons.Default.DynamicFeed,
                        title = "Home feed",
                        subtitle = "Static picture posts",
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "These are intended product goals, not a guarantee that detection or selective blocking already works.",
                    style = OutLoopTheme.typography.bodySmall,
                    color = colors.textMuted,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Technical Privacy Guarantee Footer
            OutLoopCard(
                containerColor = colors.surfaceContainer,
                borderColor = colors.surfaceBorder,
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(colors.surfaceCard, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Zero-Cloud On-Device Sandbox",
                            style = OutLoopTheme.typography.labelMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Local diagnostics only. You control when they run. Results never leave your device.",
                            style = OutLoopTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessibleFeatureTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val colors = OutLoopTheme.colors

    OutLoopCard(
        modifier = modifier,
        containerColor = colors.surfaceCard,
        borderColor = colors.surfaceBorder,
        shape = RoundedCornerShape(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(colors.surfaceContainer, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = OutLoopTheme.typography.labelMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Accessible",
                        tint = colors.accentLime,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = subtitle,
                    style = OutLoopTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
