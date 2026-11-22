package com.focusguard.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-registers the current profile's geofences after device startup. */
class GeofenceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pending = goAsync()
            LocationGeofenceManager.sync(context) { _, _ -> pending.finish() }
        }
    }
}
