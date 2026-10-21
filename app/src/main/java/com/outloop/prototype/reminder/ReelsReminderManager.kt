package com.outloop.prototype.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.outloop.classifier.ClassificationResult
import com.outloop.prototype.R
import com.outloop.prototype.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller bridging [ReelsSessionTracker] with the Android notification system, SharedPreferences, and Compose UI.
 *
 * SAFETY & POLICY:
 * - True opt-in defaults: Master reminder and advance warning are OFF (false) on first launch.
 * - Enabling advance warning never enables master reminder.
 * - Preferences are persisted via SharedPreferences across app launches.
 * - Notifications are standard, dismissible alerts with zero window overlay or screen hijacking.
 * - "Take a Break" only opens OutLoop on user tap; never executes automatic navigation or blocking.
 * - Handles notification permission denial and muted channels gracefully without crashing.
 */
object ReelsReminderManager {

    const val CHANNEL_ID = "reels_reminder_channel"
    const val REELS_REMINDER_NOTIFICATION_ID = 2001

    private const val PREFS_NAME = "outloop_reminder_preferences"
    private const val KEY_REMINDER_ENABLED = "key_reminder_enabled"
    private const val KEY_ADVANCE_WARNING_ENABLED = "key_advance_warning_enabled"

    /** Monotonic session tracker instance. */
    val tracker = ReelsSessionTracker(
        clock = { SystemClock.elapsedRealtime() },
        logger = { message -> android.util.Log.d("OutLoopReminder", message) }
    )

    private val _accumulatedTimeMs = MutableStateFlow(0L)
    val accumulatedTimeMs: StateFlow<Long> = _accumulatedTimeMs.asStateFlow()

    private val _sessionState = MutableStateFlow(ReelsSessionTracker.SessionState.IDLE)
    val sessionState: StateFlow<ReelsSessionTracker.SessionState> = _sessionState.asStateFlow()

    private val _lastRejectionReason = MutableStateFlow<String?>(null)
    val lastRejectionReason: StateFlow<String?> = _lastRejectionReason.asStateFlow()

    // TRUE OPT-IN DEFAULTS: initially OFF (false)
    private val _isReminderEnabled = MutableStateFlow(false)
    val isReminderEnabled: StateFlow<Boolean> = _isReminderEnabled.asStateFlow()

    // Advance warning also initially OFF (false)
    private val _isAdvanceWarningEnabled = MutableStateFlow(false)
    val isAdvanceWarningEnabled: StateFlow<Boolean> = _isAdvanceWarningEnabled.asStateFlow()

    private val _lastNotificationStatus = MutableStateFlow<String?>("INITIAL")
    val lastNotificationStatus: StateFlow<String?> = _lastNotificationStatus.asStateFlow()

    private var isPreferencesInitialized = false

    /**
     * Initializes user preferences from SharedPreferences on application/service start.
     */
    fun initPreferences(context: Context) {
        if (isPreferencesInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val reminderEnabled = prefs.getBoolean(KEY_REMINDER_ENABLED, false)
        val advanceWarningEnabled = prefs.getBoolean(KEY_ADVANCE_WARNING_ENABLED, false)

        _isReminderEnabled.value = reminderEnabled
        _isAdvanceWarningEnabled.value = advanceWarningEnabled
        tracker.isReminderEnabled = reminderEnabled
        tracker.isAdvanceWarningEnabled = advanceWarningEnabled
        isPreferencesInitialized = true
    }

    /**
     * Initializes the notification channel on Android 8.0+ (API 26+).
     */
    fun ensureNotificationChannel(context: Context) {
        initPreferences(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Reels Reminders"
            val descriptionText = "Mindful reminders when spending continuous time on Instagram Reels"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Handles an incoming [ClassificationResult] from the accessibility service.
     */
    fun onClassificationResult(context: Context, result: ClassificationResult) {
        initPreferences(context)
        val events = tracker.onClassificationResult(result)
        _accumulatedTimeMs.value = tracker.accumulatedReelsTimeMs
        _sessionState.value = tracker.sessionState
        _lastRejectionReason.value = tracker.lastRejectionReason

        if (events.isNotEmpty() && _isReminderEnabled.value) {
            for (event in events) {
                dispatchReminderNotification(context, event)
            }
        }
    }

    /**
     * Synchronizes and exposes current dynamic state from the tracker to UI StateFlows.
     */
    fun syncState() {
        tracker.syncState()
        _accumulatedTimeMs.value = tracker.accumulatedReelsTimeMs
        _sessionState.value = tracker.sessionState
        _lastRejectionReason.value = tracker.lastRejectionReason
    }

    /**
     * Toggles the master reminder feature on or off.
     */
    fun setReminderEnabled(context: Context, enabled: Boolean) {
        _isReminderEnabled.value = enabled
        tracker.isReminderEnabled = enabled

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()

        if (!enabled) {
            cancelNotification(context)
            _lastNotificationStatus.value = "REMINDERS_DISABLED"
        }
    }

    /**
     * Toggles the 9-minute advance warning on or off.
     * Note: Enabling advance warning NEVER enables the master switch.
     */
    fun setAdvanceWarningEnabled(context: Context, enabled: Boolean) {
        _isAdvanceWarningEnabled.value = enabled
        tracker.isAdvanceWarningEnabled = enabled

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ADVANCE_WARNING_ENABLED, enabled).apply()
    }

    /**
     * Explicitly resets the accumulated session time.
     */
    fun resetSession(context: Context) {
        tracker.resetSession()
        _accumulatedTimeMs.value = 0L
        _sessionState.value = tracker.sessionState
        _lastRejectionReason.value = null
        cancelNotification(context)
        _lastNotificationStatus.value = "SESSION_RESET"
    }

    /**
     * Dispatches a notification for a triggered [ReelsSessionTracker.ReminderEvent].
     */
    fun dispatchReminderNotification(context: Context, event: ReelsSessionTracker.ReminderEvent) {
        ensureNotificationChannel(context)

        // Permission check for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                _lastNotificationStatus.value = "NOTIFICATION_SUPPRESSED_PERMISSION_DENIED"
                return
            }
        }

        val notificationManagerCompat = NotificationManagerCompat.from(context)
        if (!notificationManagerCompat.areNotificationsEnabled()) {
            _lastNotificationStatus.value = "NOTIFICATION_SUPPRESSED_NOTIFICATIONS_MUTED"
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManagerCompat.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                _lastNotificationStatus.value = "NOTIFICATION_SUPPRESSED_CHANNEL_MUTED"
                return
            }
        }

        val minutes = (event.thresholdMs / (60 * 1000L)).toInt()

        val (title, body) = when (event.type) {
            ReelsSessionTracker.ReminderType.ADVANCE_WARNING -> {
                "OutLoop — 9 Minutes on Reels" to "Your 10-minute reminder is coming up in 1 minute. Check in with your intention."
            }
            ReelsSessionTracker.ReminderType.FIRST_REMINDER -> {
                "OutLoop — 10 Minutes on Reels" to "You have reached 10 minutes on Reels. Take a mindful break or continue intentionally."
            }
            ReelsSessionTracker.ReminderType.REPEAT_REMINDER -> {
                "OutLoop — $minutes Minutes on Reels" to "You've been on Reels for $minutes minutes. Still enjoying it, or ready for a break?"
            }
        }

        // Action 1: Dismiss
        val dismissIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_DISMISS_REMINDER
            putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, REELS_REMINDER_NOTIFICATION_ID)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Take a Break
        val breakIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val breakPendingIntent = PendingIntent.getActivity(
            context,
            102,
            breakIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(breakPendingIntent)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .addAction(0, "Take a Break", breakPendingIntent)
            .build()

        try {
            notificationManagerCompat.notify(REELS_REMINDER_NOTIFICATION_ID, notification)
            _lastNotificationStatus.value = "DELIVERED_${event.type.name}"
        } catch (e: SecurityException) {
            _lastNotificationStatus.value = "NOTIFICATION_SUPPRESSED_SECURITY_EXCEPTION"
        }
    }

    fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(REELS_REMINDER_NOTIFICATION_ID)
    }
}
