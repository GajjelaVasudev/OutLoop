package com.outloop.prototype.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.outloop.prototype.reminder.ReelsReminderManager
import com.outloop.prototype.reminder.ReelsSessionTracker
import com.outloop.prototype.ui.theme.AccentAmber
import com.outloop.prototype.ui.theme.AccentEmerald
import com.outloop.prototype.ui.theme.PrimaryIndigo
import com.outloop.prototype.ui.theme.SurfaceDark
import com.outloop.prototype.ui.theme.SurfaceLightDark
import com.outloop.prototype.ui.theme.TextMuted
import com.outloop.prototype.ui.theme.TextPrimary
import com.outloop.prototype.ui.theme.TextSecondary

@Composable
fun ReminderSettingsCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Synchronize tracker state on display and activity resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ReelsReminderManager.initPreferences(context)
                ReelsReminderManager.syncState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        ReelsReminderManager.initPreferences(context)
        ReelsReminderManager.syncState()
    }

    val accumulatedTimeMs by ReelsReminderManager.accumulatedTimeMs.collectAsState()
    val sessionState by ReelsReminderManager.sessionState.collectAsState()
    val isReminderEnabled by ReelsReminderManager.isReminderEnabled.collectAsState()
    val isAdvanceWarningEnabled by ReelsReminderManager.isAdvanceWarningEnabled.collectAsState()
    val lastRejectionReason by ReelsReminderManager.lastRejectionReason.collectAsState()

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
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
                        imageVector = if (isReminderEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        contentDescription = "Reminders",
                        tint = if (isReminderEnabled) PrimaryIndigo else TextMuted
                    )
                    Text(
                        text = "Selective Reels Reminder",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Master Toggle (Defaults to OFF)
                Switch(
                    checked = isReminderEnabled,
                    onCheckedChange = { ReelsReminderManager.setReminderEnabled(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PrimaryIndigo
                    )
                )
            }

            Text(
                text = "Opt-in mindful alerts based on confirmed time spent on Instagram's dedicated Reels tab. Both reminders and advance warnings are OFF by default until explicitly enabled.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            // Timer display & Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceLightDark, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ACCUMULATED REELS TIME",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    val minutes = (accumulatedTimeMs / 1000) / 60
                    val seconds = (accumulatedTimeMs / 1000) % 60
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val (stateText, stateColor) = when (sessionState) {
                        ReelsSessionTracker.SessionState.TRACKING -> "TRACKING" to AccentEmerald
                        ReelsSessionTracker.SessionState.PAUSED -> "PAUSED" to AccentAmber
                        ReelsSessionTracker.SessionState.IDLE -> "IDLE" to TextMuted
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(8.dp)
                                .background(stateColor, CircleShape)
                        )
                        Text(
                            text = stateText,
                            color = stateColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val nextThresholdMs = ReelsReminderManager.tracker.getNextThresholdMs()
                    val nextMin = (nextThresholdMs / (60 * 1000)).toInt()
                    val subtitleText = if (isReminderEnabled) {
                        if (sessionState == ReelsSessionTracker.SessionState.PAUSED && !lastRejectionReason.isNullOrEmpty()) {
                            "$lastRejectionReason • Next: ${nextMin}m"
                        } else {
                            "Next: ${nextMin}m threshold"
                        }
                    } else {
                        "Reminders OFF"
                    }
                    Text(
                        text = subtitleText,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Sub-options: Advance Warning
            // Inactive/disabled when master reminder is OFF; enabling advance warning NEVER enables master switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "9-Minute Advance Warning",
                        color = if (isReminderEnabled) TextPrimary else TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isReminderEnabled) {
                            "Fires 1 minute prior to the 10-minute first reminder"
                        } else {
                            "Requires enabling reminders above"
                        },
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isAdvanceWarningEnabled,
                    enabled = isReminderEnabled,
                    onCheckedChange = { ReelsReminderManager.setAdvanceWarningEnabled(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PrimaryIndigo
                    )
                )
            }

            // Notification Permission Banner for Android 13+
            if (isReminderEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Permission needed for alert delivery over Instagram",
                            color = AccentAmber,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Grant", color = Color.Black, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Reset action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = { ReelsReminderManager.resetSession(context) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Timer",
                        tint = TextSecondary,
                        modifier = Modifier.width(16.dp).height(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Timer", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}
