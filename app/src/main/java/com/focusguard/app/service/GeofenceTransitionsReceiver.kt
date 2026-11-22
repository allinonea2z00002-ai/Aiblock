package com.focusguard.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.focusguard.app.data.BlockStore

/** Receives Play Services geofence transitions and updates the local active-area state. */
class GeofenceTransitionsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val ids = event.triggeringGeofences?.map { it.requestId }?.toSet().orEmpty()
        if (ids.isEmpty()) return
        val store = BlockStore(context)
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> store.setActiveGeofences(store.activeGeofences() + ids)
            Geofence.GEOFENCE_TRANSITION_EXIT -> store.setActiveGeofences(store.activeGeofences() - ids)
        }
    }
}
