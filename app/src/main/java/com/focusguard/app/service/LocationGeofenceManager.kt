package com.focusguard.app.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.focusguard.app.data.BlockStore

/** Registers the current profile's saved circular geofences with Google Play services. */
object LocationGeofenceManager {
    private const val REQUEST_CODE = 7402

    fun hasForegroundPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundPermission(context: Context): Boolean = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun sync(context: Context, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (!hasForegroundPermission(context)) {
            onResult(false, "Location permission is not granted.")
            return
        }
        if (!hasBackgroundPermission(context)) {
            onResult(false, "Allow background location for geofencing while the app is closed.")
            return
        }
        val client = LocationServices.getGeofencingClient(context)
        val pendingIntent = pendingIntent(context)
        client.removeGeofences(pendingIntent).addOnCompleteListener {
            val geofences = BlockStore(context).locationRules().map { rule ->
                Geofence.Builder()
                    .setRequestId(rule.id)
                    .setCircularRegion(rule.latitude, rule.longitude, rule.radiusMeters)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .setLoiteringDelay(0)
                    .setNotificationResponsiveness(10_000)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .build()
            }
            if (geofences.isEmpty()) {
                onResult(true, "No saved location zones. Geofences cleared.")
                return@addOnCompleteListener
            }
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build()
            try {
                client.addGeofences(request, pendingIntent)
                    .addOnSuccessListener { onResult(true, "${geofences.size} location zone(s) registered.") }
                    .addOnFailureListener { onResult(false, it.localizedMessage ?: "Could not register geofences.") }
            } catch (security: SecurityException) {
                onResult(false, security.localizedMessage ?: "Location permission was revoked.")
            }
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceTransitionsReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
