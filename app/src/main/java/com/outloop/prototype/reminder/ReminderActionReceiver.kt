package com.outloop.prototype.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver handling explicit user actions on OutLoop reminder notifications.
 *
 * SAFETY GUARANTEES:
 * - Dismiss action cancels the notification alert.
 * - Dismissing does NOT reset the accumulated session time or disable reminders;
 *   subsequent repeat reminders remain scheduled as expected.
 * - Does not interact with or modify Instagram.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISMISS_REMINDER = "com.outloop.prototype.ACTION_DISMISS_REMINDER"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == ACTION_DISMISS_REMINDER) {
            val notificationId = intent.getIntExtra(
                EXTRA_NOTIFICATION_ID,
                ReelsReminderManager.REELS_REMINDER_NOTIFICATION_ID
            )
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(notificationId)
        }
    }
}
