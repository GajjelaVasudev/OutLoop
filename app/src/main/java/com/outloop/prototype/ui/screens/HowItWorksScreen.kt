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
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.outloop.prototype.ui.components.OutLoopButton
import com.outloop.prototype.ui.components.OutLoopButtonStyle
import com.outloop.prototype.ui.components.OutLoopCard
import com.outloop.prototype.ui.components.OutLoopStatusBadge
import com.outloop.prototype.ui.theme.OutLoopTheme

/**
 * Screen 2: How OutLoop Works (Stitch Reference: 2._how_outloop_works)
 * Visual source of truth: stitch_reference/2._how_outloop_works/ & _dark_mode/
 */
@Composable
fun HowItWorksScreen(
    onContinue: () -> Unit,
    onLearnAboutPrivacy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OutLoopTheme.colors
    val scrollState = rememberScrollState()
    var isDemoSwitchChecked by remember { mutableStateOf(true) }

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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AllInclusive,
                        contentDescription = null,
                        tint = colors.accentLime,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "How It Works",
                        style = OutLoopTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutLoopStatusBadge(
                    text = "STEP 1 OF 3",
                    containerColor = colors.surfaceContainer,
                    contentColor = colors.textSecondary
                )
            }

            // Headline & Subtitle
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "How OutLoop works",
                    style = OutLoopTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "A transparent approach to reclaiming your time while keeping what you value.",
                    style = OutLoopTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    lineHeight = 20.sp
                )
            }

            // Step Cards
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Card 01: Choose what matters
                OutLoopCard(
                    containerColor = colors.surfaceCard,
                    borderColor = colors.surfaceBorder,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(colors.accentLime, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "01",
                                    color = colors.onAccentLime,
                                    style = OutLoopTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutLoopStatusBadge(
                                text = "RETAINED",
                                containerColor = colors.statusSuccess.copy(alpha = 0.2f),
                                contentColor = colors.statusSuccess
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Choose what matters",
                                style = OutLoopTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Keep access to DMs, Stories, profiles, and your usual feed.",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }

                        // Preserved Surfaces Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("DMs", "Stories", "Profiles", "Feed").forEach { surface ->
                                Surface(
                                    shape = CircleShape,
                                    color = colors.surfaceContainer,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.surfaceBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = colors.statusSuccess,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = surface,
                                            style = OutLoopTheme.typography.labelSmall,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 02: Understand the loop
                OutLoopCard(
                    containerColor = colors.surfaceCard,
                    borderColor = colors.surfaceBorder,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(colors.surfaceContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "02",
                                    color = colors.textPrimary,
                                    style = OutLoopTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutLoopStatusBadge(
                                text = "NON-INVASIVE DETECTION",
                                containerColor = colors.surfaceContainer,
                                contentColor = colors.textSecondary
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Understand the loop",
                                style = OutLoopTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "OutLoop investigates whether Instagram's dedicated Reels tab can be identified reliably using Android UI structures.",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }

                        // Inspection Architecture Pill
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surfaceContainer, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(colors.surfaceCard, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FindInPage,
                                    contentDescription = null,
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Accessibility Node Inspection",
                                    style = OutLoopTheme.typography.labelMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Zero screen capture, zero network transmission",
                                    style = OutLoopTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // Card 03: Stay in control
                OutLoopCard(
                    containerColor = colors.surfaceCard,
                    borderColor = colors.surfaceBorder,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(colors.surfaceContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "03",
                                    color = colors.textPrimary,
                                    style = OutLoopTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isDemoSwitchChecked) "Diagnostics: Active" else "Diagnostics: Paused",
                                    style = OutLoopTheme.typography.labelSmall,
                                    color = if (isDemoSwitchChecked) colors.accentLime else colors.textMuted,
                                    fontWeight = FontWeight.Bold
                                )
                                Switch(
                                    checked = isDemoSwitchChecked,
                                    onCheckedChange = { isDemoSwitchChecked = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.onAccentLime,
                                        checkedTrackColor = colors.accentLime,
                                        uncheckedThumbColor = colors.textMuted,
                                        uncheckedTrackColor = colors.surfaceContainer
                                    )
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Stay in control",
                                style = OutLoopTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "You decide when diagnostics run and can pause or cease telemetry instantly at any time.",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Prototype Transparency Banner
            OutLoopCard(
                containerColor = colors.surfaceContainer,
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
                            .background(colors.surfaceCard, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Early prototype notice",
                            style = OutLoopTheme.typography.labelMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "This version does not block Instagram yet. It only benchmarks detection accuracy and battery impact.",
                            style = OutLoopTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Bottom Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutLoopButton(
                    text = "Continue",
                    onClick = onContinue,
                    style = OutLoopButtonStyle.PrimaryKinetic,
                    trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
                )

                TextButton(
                    onClick = onLearnAboutPrivacy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Learn about privacy",
                        style = OutLoopTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}
