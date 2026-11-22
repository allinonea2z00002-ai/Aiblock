package com.focusguard.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.focusguard.app.data.BlockStore

/**
 * Optional notification filter. The user must explicitly enable Notification Access
 * in Android Settings. Only notifications from packages selected by the user are cancelled.
 */
class FocusNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        val store = BlockStore(this)
        if (store.feature("schedule_notification") && store.scheduleEnabled() && !store.isWithinSchedule()) return
        if (sbn.packageName in store.notificationBlockedApps()) {
            try { cancelNotification(sbn.key) } catch (_: SecurityException) { }
        }
    }
}
