package com.outloop.prototype.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.outloop.prototype.history.AttentionHistoryRepository
import com.outloop.prototype.reward.AttentionRewardManager
import com.outloop.prototype.ui.components.OutLoopButton
import com.outloop.prototype.ui.components.OutLoopButtonStyle
import com.outloop.prototype.ui.components.OutLoopStatusBadge
import com.outloop.prototype.ui.theme.LabelMonoMedium
import com.outloop.prototype.ui.theme.OutLoopTheme
import kotlinx.coroutines.delay

/**
 * Optional 60-second Scroll Rescue mindful reset dialog.
 *
 * SAFETY & INTEGRITY REQUIREMENTS:
 * - This reset does not pretend to be medical treatment.
 * - This reset is entirely optional and never shames the user.
 * - Does not grant extra Reels time when started, completed, or dismissed.
 * - Does not erase accumulated usage or bypass the daily restriction.
 */
@Composable
fun ScrollRescueResetDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = OutLoopTheme.colors

    var secondsRemaining by remember { mutableIntStateOf(60) }
    var isRunning by remember { mutableStateOf(true) }
    var isCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, secondsRemaining) {
        if (isRunning && secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining -= 1
            if (secondsRemaining == 0) {
                isCompleted = true
                isRunning = false

                val today = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    java.time.LocalDate.now().toString()
                } else {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                }
                val repo = AttentionHistoryRepository.getInstance(context)
                repo.recordScrollRescueResetCompleted(today)
                AttentionRewardManager.getInstance(context).evaluateRewards(repo.allRecords.value)
            }
        }
    }

    val progress = (60 - secondsRemaining) / 60f

    val guidanceText = when {
        isCompleted -> "Reset complete! Take this calm focus with you as you choose what's next."
        secondsRemaining > 45 -> "Take a slow, deep breath in... and let it gently out."
        secondsRemaining > 30 -> "Look away from the screen. Let your eyes focus on something distant."
        secondsRemaining > 15 -> "Drop your shoulders and unclench your jaw and hands."
        else -> "Notice how you feel right now. Set an intentional next step."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        titleContentColor = colors.textPrimary,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(colors.accentLime.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelfImprovement,
                            contentDescription = null,
                            tint = colors.accentLime,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Scroll Rescue Reset",
                        style = OutLoopTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Non-medical disclosure pill
                OutLoopStatusBadge(
                    text = "NON-MEDICAL WELLNESS PAUSE",
                    containerColor = colors.surfaceContainer,
                    contentColor = colors.textSecondary
                )

                // Circular countdown
                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { if (isCompleted) 1f else progress },
                        modifier = Modifier.size(120.dp),
                        color = if (isCompleted) colors.accentLime else colors.accentLime,
                        trackColor = colors.surfaceContainer,
                        strokeWidth = 6.dp
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = colors.accentLime,
                                modifier = Modifier.size(38.dp)
                            )
                        } else {
                            Text(
                                text = "${secondsRemaining}s",
                                style = LabelMonoMedium,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "REMAINING",
                                style = OutLoopTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = colors.textMuted
                            )
                        }
                    }
                }

                // Rotating guidance prompt
                AnimatedContent(
                    targetState = guidanceText,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "GuidancePrompt"
                ) { text ->
                    Text(
                        text = text,
                        style = OutLoopTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // Footnote reminding that allowance is preserved
                Text(
                    text = "This optional pause is for reflection only. It does not grant extra Reels time or change your daily allowance.",
                    style = OutLoopTheme.typography.bodySmall,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            if (isCompleted) {
                OutLoopButton(
                    text = "Done",
                    onClick = onDismiss,
                    style = OutLoopButtonStyle.PrimaryKinetic,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Skip Pause",
                            color = colors.textSecondary,
                            style = OutLoopTheme.typography.labelMedium
                        )
                    }

                    OutLoopButton(
                        text = if (isRunning) "Pause" else "Resume",
                        onClick = { isRunning = !isRunning },
                        style = OutLoopButtonStyle.Secondary,
                        leadingIcon = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow
                    )
                }
            }
        }
    )
}
