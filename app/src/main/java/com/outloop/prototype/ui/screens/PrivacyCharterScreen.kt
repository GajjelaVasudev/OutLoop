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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.VerifiedUser
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
import com.outloop.prototype.ui.theme.OutLoopTheme

/**
 * Screen 3: Privacy & Accessibility Explanation (Stitch Reference: 3._privacy_accessibility_explanation)
 * Visual source of truth: stitch_reference/3._privacy_accessibility_explanation/ & _dark_mode/
 */
@Composable
fun PrivacyCharterScreen(
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
    onBack: () -> Unit,
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

                Text(
                    text = "Privacy Flow",
                    style = OutLoopTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                OutLoopStatusBadge(
                    text = "STEP 2 OF 3",
                    containerColor = colors.surfaceContainer,
                    contentColor = colors.textSecondary,
                    pulse = true
                )
            }

            // Headline & Plain Language Subtitle
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = colors.accentLime,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "PRIVACY CHARTER",
                        style = OutLoopTheme.typography.labelSmall,
                        color = colors.accentLime,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Your privacy comes first.",
                    style = OutLoopTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "OutLoop's prototype uses Android AccessibilityService to inspect limited interface structure while Instagram is open. It does not need to read your messages or collect your personal content.",
                    style = OutLoopTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    lineHeight = 20.sp
                )
            }

            // Trust Verification Visual Anchor
            OutLoopCard(
                containerColor = colors.surfaceContainer,
                borderColor = colors.surfaceBorder,
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.accentLime.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = colors.accentLime,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local-Only Processing Pipeline",
                            style = OutLoopTheme.typography.labelLarge,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Strictly bounded sandbox • Zero network socket access",
                            style = OutLoopTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            // Three Clean Core Principles
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Point 1
                OutLoopCard(
                    containerColor = colors.surfaceCard,
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
                                .size(36.dp)
                                .background(colors.surfaceContainer, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Diagnostics are off until you start a session",
                                style = OutLoopTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "You decide when analysis begins. Zero background monitoring when inactive.",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Point 2
                OutLoopCard(
                    containerColor = colors.surfaceCard,
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
                                .size(36.dp)
                                .background(colors.surfaceContainer, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Results stay on your device",
                                style = OutLoopTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Everything runs locally. No cloud sync, no tracking, zero telemetry sent to remote servers.",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Point 3
                OutLoopCard(
                    containerColor = colors.surfaceCard,
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
                                .size(36.dp)
                                .background(colors.surfaceContainer, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ToggleOff,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Stop or disable at any time",
                                style = OutLoopTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "You can pause diagnostics instantly or revoke the Android Accessibility permission in system settings.",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Prominent Disclosure Banner
            OutLoopCard(
                containerColor = colors.surfaceCard,
                borderColor = colors.surfaceBorder,
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(colors.surfaceContainer, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Accessibility Purpose & Scope: OutLoop uses Android AccessibilityService observations strictly on Instagram to track continuous time spent on the dedicated Reels tab. When your self-imposed daily allowance expires, OutLoop may display a mindful restriction screen over Instagram to help you pause, while leaving Direct Messages, Stories, Profiles, and Home Feed fully accessible.",
                        style = OutLoopTheme.typography.bodySmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Subtle Informative Callout (Consent Safeguard)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceContainer, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "This screen leads to a separate confirmation step before opening Android Accessibility settings. OutLoop never opens settings automatically.",
                    style = OutLoopTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            // Interactive Action Module
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutLoopButton(
                    text = "I understand — continue",
                    onClick = onContinue,
                    style = OutLoopButtonStyle.PrimaryKinetic,
                    trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
                )

                OutLoopButton(
                    text = "Not now",
                    onClick = onNotNow,
                    style = OutLoopButtonStyle.Secondary
                )
            }
        }
    }
}
