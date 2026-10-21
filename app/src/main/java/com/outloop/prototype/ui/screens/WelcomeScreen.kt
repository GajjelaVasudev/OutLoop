package com.outloop.prototype.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.PsychologyAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * Screen 1: Welcome & Onboarding (Stitch Reference: 1._welcome_onboarding)
 * Visual source of truth: stitch_reference/1._welcome_onboarding/ & _dark_mode/
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
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
            // Brand Top Bar
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
                            .background(colors.surfaceContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AllInclusive,
                            contentDescription = "OutLoop Logo",
                            tint = colors.accentLime,
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

                OutLoopStatusBadge(
                    text = "v0.4 PREVIEW",
                    containerColor = colors.surfaceContainer,
                    contentColor = colors.textSecondary,
                    pulse = true
                )
            }

            // Eyebrow Pill
            Row {
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.surfaceBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = colors.accentLime,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "EARLY PROTOTYPE • LOCAL DETECTION",
                            style = OutLoopTheme.typography.labelSmall,
                            color = colors.accentLime,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Headline & Subtitle
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Break the loop.\nKeep the connection.",
                    style = OutLoopTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 34.sp
                )
                Text(
                    text = "Reduce endless Reels scrolling without giving up the Instagram features you use every day.",
                    style = OutLoopTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                    lineHeight = 22.sp
                )
            }

            // Central Dynamic Broken-Loop Vector Visual
            OutLoopCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = colors.surfaceCard,
                borderColor = colors.surfaceBorder,
                shape = RoundedCornerShape(20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BrokenLoopGraphic(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        isDark = colors.isDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(colors.accentLime, CircleShape)
                        )
                        Text(
                            text = "Reel triggers redirected to mindful pause",
                            style = OutLoopTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Value Proposition Cards
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Card 1: Keep what matters
                OutLoopCard(
                    containerColor = colors.surfaceCard,
                    borderColor = colors.surfaceBorder,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(colors.accentLime.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = colors.accentLime,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Keep what matters",
                                style = OutLoopTheme.typography.labelLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Full access to DMs, Stories, Profiles, and your Main Feed",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Card 2: Intentional attention
                OutLoopCard(
                    containerColor = colors.surfaceCard,
                    borderColor = colors.surfaceBorder,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(colors.surfaceContainer, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PsychologyAlt,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Intentional attention",
                                style = OutLoopTheme.typography.labelLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Designed for conscious use, never guilt or digital isolation",
                                style = OutLoopTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.LockReset,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Bottom Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutLoopButton(
                    text = "Get started",
                    onClick = onGetStarted,
                    style = OutLoopButtonStyle.PrimaryKinetic,
                    trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
                )

                Text(
                    text = "Designed for intentional scrolling, not digital isolation. No automated blocking in this prototype.",
                    style = OutLoopTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "LOCAL RESEARCH PROTOTYPE • DETECTION IN ACTIVE TESTING",
                        style = LabelMonoSmall,
                        color = colors.textMuted
                    )
                }
            }
        }
    }
}

/**
 * Custom Canvas vector visual reproducing Stitch's broken loop turning into forward momentum.
 */
@Composable
private fun BrokenLoopGraphic(
    modifier: Modifier = Modifier,
    isDark: Boolean = true
) {
    val trackColor = if (isDark) Color(0xFF434937) else Color(0xFFE2E3DF)
    val loopColor = if (isDark) Color(0xFFF7F8F4) else Color(0xFF1A1C1A)
    val accentLime = Color(0xFFB9F45A)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val centerX = width * 0.32f
        val centerY = height * 0.52f
        val radius = height * 0.35f

        // 1. Dashed Habitual Circle (Trapped loop sinkhole)
        drawCircle(
            color = trackColor,
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(
                width = 4f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
            )
        )

        // 2. Heavy Loop Arc
        drawArc(
            color = loopColor,
            startAngle = 135f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = Offset(centerX - radius, centerY - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 8f, cap = StrokeCap.Round)
        )

        // 3. Breakout kinetic line turning into forward freedom
        val breakoutPath = Path().apply {
            moveTo(centerX + radius * 0.82f, centerY + radius * 0.5f)
            cubicTo(
                centerX + radius * 1.3f, centerY + radius * 0.9f,
                width * 0.65f, height * 0.42f,
                width * 0.84f, height * 0.32f
            )
        }
        drawPath(
            path = breakoutPath,
            color = loopColor,
            style = Stroke(width = 8f, cap = StrokeCap.Round)
        )

        // 4. Directional kinetic apex indicator (Lime Accent)
        val endPoint = Offset(width * 0.84f, height * 0.32f)
        drawCircle(
            color = accentLime.copy(alpha = 0.35f),
            radius = 22f,
            center = endPoint
        )
        drawCircle(
            color = accentLime,
            radius = 12f,
            center = endPoint
        )

        // Start point badge inside loop
        val startPoint = Offset(centerX, centerY - radius)
        drawCircle(
            color = loopColor,
            radius = 8f,
            center = startPoint
        )
    }
}
