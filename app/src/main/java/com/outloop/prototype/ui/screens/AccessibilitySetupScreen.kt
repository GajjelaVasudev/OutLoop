package com.outloop.prototype.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.outloop.prototype.ui.components.OutLoopButton
import com.outloop.prototype.ui.components.OutLoopButtonStyle
import com.outloop.prototype.ui.components.OutLoopCard
import com.outloop.prototype.ui.components.OutLoopStatusBadge
import com.outloop.prototype.ui.components.PulsingDot
import com.outloop.prototype.ui.theme.LabelMonoSmall
import com.outloop.prototype.ui.theme.OutLoopTheme

/**
 * Screen 4: Accessibility Setup Guide (Stitch Reference: 5._accessibility_setup)
 * Visual source of truth: stitch_reference/5._accessibility_setup/ & _dark_mode/
 */
@Composable
fun AccessibilitySetupScreen(
    isServiceEnabledInSettings: Boolean,
    isServiceConnected: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onFinish: () -> Unit,
    onBack: (() -> Unit)? = null,
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Navigation & Step Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(38.dp)
                            .background(colors.surfaceContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(38.dp))
                }

                Text(
                    text = "Accessibility Setup",
                    style = OutLoopTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                OutLoopStatusBadge(
                    text = "STEP 3 OF 3",
                    containerColor = colors.surfaceContainer,
                    contentColor = colors.textSecondary,
                    pulse = !isServiceEnabledInSettings
                )
            }

            // Headline & Plain Language Intro
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Connect OutLoop to Instagram",
                    style = OutLoopTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "To detect when Instagram opens its Reels feed and track time toward your mindful daily allowance, OutLoop requires standard Android accessibility access to inspect interface hierarchy structures strictly on your local device. When your allowance expires, OutLoop may display a mindful restriction screen over Reels while leaving DMs, Stories, Profiles, and Home Feed fully accessible.",
                    style = OutLoopTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    lineHeight = 20.sp
                )
            }

            // Service Architecture State Card
            OutLoopCard(
                containerColor = colors.surfaceCard,
                borderColor = colors.surfaceBorder,
                shape = RoundedCornerShape(18.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = colors.accentLime,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Service Architecture State",
                                style = OutLoopTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutLoopStatusBadge(
                            text = "OFFLINE ONLY",
                            containerColor = colors.surfaceContainer,
                            contentColor = colors.textSecondary
                        )
                    }

                    // Status Indicators 2-Column Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Sub-card 1: Permission
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(colors.surfaceContainer, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "PERMISSION",
                                    style = LabelMonoSmall,
                                    color = colors.textMuted
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val permColor = if (isServiceEnabledInSettings) colors.statusSuccess else colors.statusWarning
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(permColor, CircleShape)
                                    )
                                    Text(
                                        text = if (isServiceEnabledInSettings) "Granted" else "Not yet granted",
                                        style = OutLoopTheme.typography.bodySmall,
                                        color = if (isServiceEnabledInSettings) colors.textPrimary else colors.statusWarning,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Sub-card 2: Hook Connection
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(colors.surfaceContainer, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "HOOK CONNECTION",
                                    style = LabelMonoSmall,
                                    color = colors.textMuted
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val hookColor = if (isServiceConnected) colors.statusSuccess else colors.textMuted
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(hookColor, CircleShape)
                                    )
                                    Text(
                                        text = if (isServiceConnected) "Connected" else "Idle / Standby",
                                        style = OutLoopTheme.typography.bodySmall,
                                        color = if (isServiceConnected) colors.textPrimary else colors.textSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3-Step Quick Guide Card
            OutLoopCard(
                containerColor = colors.surfaceCard,
                borderColor = colors.surfaceBorder,
                shape = RoundedCornerShape(18.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = null,
                            tint = colors.accentLime,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "3-Step Quick Guide",
                            style = OutLoopTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Step 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(colors.accentLime, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "1",
                                color = colors.onAccentLime,
                                style = OutLoopTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Open Android Accessibility",
                                style = OutLoopTheme.typography.labelLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tap the action button below. You will be redirected directly to your phone's central Accessibility pane.",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Step 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(colors.surfaceContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "2",
                                color = colors.textPrimary,
                                style = OutLoopTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Locate OutLoop in Installed Apps",
                                style = OutLoopTheme.typography.labelLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Look under Downloaded apps or Installed services, then tap on OutLoop.",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Step 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(colors.surfaceContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "3",
                                color = colors.textPrimary,
                                style = OutLoopTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Review Disclosure & Enable",
                                style = OutLoopTheme.typography.labelLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Toggle the switch to ‘On’. Android will present its standard caution dialogue to verify your intent.",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Notice Boxes & Callouts
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Android System Notice
                OutLoopCard(
                    containerColor = colors.surfaceContainer,
                    borderColor = colors.surfaceBorder,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = colors.accentLime,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Android System Notice: Android displays a cautionary warning whenever an Accessibility Service is initiated because services can monitor active UI layouts. OutLoop operates in an isolated local sandbox with zero network telemetry or tracking.",
                            style = OutLoopTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Explicit Confirmation Callout
                OutLoopCard(
                    containerColor = colors.surfaceContainer,
                    borderColor = colors.surfaceBorder,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Explicit Confirmation: You will leave OutLoop to finalize this setup. OutLoop never grants permissions programmatically or behind the scenes.",
                            style = OutLoopTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Sticky Action CTAs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutLoopButton(
                    text = "Open Accessibility Settings",
                    onClick = onOpenAccessibilitySettings,
                    style = OutLoopButtonStyle.PrimaryKinetic,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
                )

                OutLoopButton(
                    text = if (isServiceEnabledInSettings) "Continue to OutLoop" else "Skip / Continue Anyway",
                    onClick = onFinish,
                    style = OutLoopButtonStyle.Secondary
                )
            }
        }
    }
}
