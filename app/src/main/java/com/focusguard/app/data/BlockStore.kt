package com.focusguard.app.data

import android.content.Context
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

/** Persistent local settings. No account or cloud service is required. */
class BlockStore(context: Context) {
    private val prefs = context.getSharedPreferences("focus_guard", Context.MODE_PRIVATE)
    // Blocking rules are isolated by the currently selected profile.
    private fun scoped(name: String) = "profile_${activeProfile().trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")}_$name"
    private fun readSet(name: String, legacy: String = name): Set<String> =
        (prefs.getStringSet(scoped(name), null) ?: if (activeProfile() == "Default") prefs.getStringSet(legacy, emptySet()) else emptySet())?.toSet() ?: emptySet()
    private fun writeSet(name: String, values: Set<String>) { prefs.edit().putStringSet(scoped(name), values.toSet()).apply() }

    fun blockedApps(): Set<String> = readSet("blocked_apps")
    fun blockedSites(): Set<String> = readSet("blocked_sites")
    fun keywords(): Set<String> = readSet("keywords")
    fun notificationBlockedApps(): Set<String> = readSet("notification_blocked_apps")
    fun addNotificationBlockedApp(value: String) { writeSet("notification_blocked_apps", notificationBlockedApps() + value) }
    fun removeNotificationBlockedApp(value: String) { writeSet("notification_blocked_apps", notificationBlockedApps() - value) }
    fun isWhitelistMode(): Boolean = prefs.getBoolean(scoped("whitelist_mode"), if (activeProfile() == "Default") prefs.getBoolean("whitelist_mode", false) else false)
    fun setWhitelistMode(value: Boolean) { prefs.edit().putBoolean(scoped("whitelist_mode"), value).apply() }
    fun allowedApps(): Set<String> = readSet("allowed_apps")
    fun addAllowedApp(value: String) { writeSet("allowed_apps", allowedApps() + value) }
    fun removeAllowedApp(value: String) { if (!isStrict()) writeSet("allowed_apps", allowedApps() - value) }
    fun profiles(): Set<String> = prefs.getStringSet("profiles", emptySet())?.toSet() ?: emptySet()
    fun addProfile(value: String) { if (value.isNotBlank()) prefs.edit().putStringSet("profiles", profiles() + value.trim()).apply() }
    fun activeProfile(): String = prefs.getString("active_profile", "Default") ?: "Default"
    fun setActiveProfile(value: String) { prefs.edit().putString("active_profile", value).apply() }
    fun isStrict(): Boolean = prefs.getBoolean("strict_mode", false)
    fun setStrict(value: Boolean) { prefs.edit().putBoolean("strict_mode", value).apply() }
    fun addApp(value: String) { writeSet("blocked_apps", blockedApps() + value) }
    fun removeApp(value: String) { if (!isStrict()) writeSet("blocked_apps", blockedApps() - value) }
    fun addSite(value: String) { val host = normalizeHost(value); if (host.isNotBlank()) writeSet("blocked_sites", blockedSites() + host) }
    fun removeSite(value: String) { if (!isStrict()) writeSet("blocked_sites", blockedSites() - normalizeHost(value)) }
    fun addKeyword(value: String) { writeSet("keywords", keywords() + value.trim().lowercase()) }
    fun removeKeyword(value: String) { if (!isStrict()) writeSet("keywords", keywords() - value.trim().lowercase()) }

    // Daily limits are profile-scoped. Keep legacy global limits readable for Default
    // so upgrading an existing installation does not silently discard its settings.
    private fun limitKey(packageName: String) = scoped("limit_$packageName")
    fun dailyLimitMinutes(packageName: String): Int =
        prefs.getInt(limitKey(packageName), if (activeProfile() == "Default") prefs.getInt("limit_$packageName", 0) else 0)
    fun setDailyLimit(packageName: String, minutes: Int) {
        prefs.edit().putInt(limitKey(packageName), minutes.coerceAtLeast(0)).apply()
    }
    fun dailyLimits(): Map<String, Int> {
        val prefix = scoped("limit_")
        val current = prefs.all.filterKeys { it.startsWith(prefix) }
            .mapKeys { it.key.removePrefix(prefix) }
            .mapValues { (it.value as? Int) ?: 0 }
        val merged = if (activeProfile() == "Default") {
            val legacy = prefs.all.filterKeys { it.startsWith("limit_") && !it.startsWith("profile_") }
                .mapKeys { it.key.removePrefix("limit_") }
                .mapValues { (it.value as? Int) ?: 0 }
            legacy + current
        } else current
        return merged.filterValues { it > 0 }
    }

    // Weekly schedules are profile-scoped. Existing installations retain their old
    // device-wide schedule as the Default profile's migration fallback.
    private fun scheduleKey(name: String) = scoped("schedule_$name")
    fun setScheduleEnabled(value: Boolean) {
        prefs.edit().putBoolean(scheduleKey("enabled"), value).apply()
    }
    fun scheduleEnabled(): Boolean = prefs.getBoolean(
        scheduleKey("enabled"), if (activeProfile() == "Default") prefs.getBoolean("schedule_enabled", false) else false
    )
    fun scheduleStartMinute(): Int = prefs.getInt(
        scheduleKey("start"), if (activeProfile() == "Default") prefs.getInt("schedule_start", 8 * 60) else 8 * 60
    )
    fun scheduleEndMinute(): Int = prefs.getInt(
        scheduleKey("end"), if (activeProfile() == "Default") prefs.getInt("schedule_end", 17 * 60) else 17 * 60
    )
    fun scheduleDays(): Set<Int> = (prefs.getStringSet(
        scheduleKey("days"), if (activeProfile() == "Default") prefs.getStringSet("schedule_days", setOf("2", "3", "4", "5", "6")) else setOf("2", "3", "4", "5", "6")
    ) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
    fun setSchedule(start: Int, end: Int, days: Set<Int>) {
        prefs.edit().putInt(scheduleKey("start"), start.coerceIn(0, 1439))
            .putInt(scheduleKey("end"), end.coerceIn(0, 1439))
            .putStringSet(scheduleKey("days"), days.map(Int::toString).toSet()).apply()
    }
    fun isWithinSchedule(now: Calendar = Calendar.getInstance()): Boolean {
        if (!scheduleEnabled()) return false
        val day = now.get(Calendar.DAY_OF_WEEK)
        val days = scheduleDays()
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = scheduleStartMinute()
        val end = scheduleEndMinute()
        if (start == end) return day in days
        return if (end > start) {
            day in days && minute in start until end
        } else {
            // For an overnight interval, the after-midnight part belongs to yesterday's selected schedule.
            if (minute >= start) day in days
            else if (minute < end) {
                val previousDay = if (day == Calendar.SUNDAY) Calendar.SATURDAY else day - 1
                previousDay in days
            } else false
        }
    }

    // Location rules are profile-scoped. Active geofence IDs are device state supplied
    // by Google Play services transition callbacks.
    private fun locationRulesKey() = scoped("location_rules")
    fun locationRules(): List<LocationRule> {
        val raw = prefs.getString(locationRulesKey(), "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val packages = item.optJSONArray("packages") ?: JSONArray()
                    val blocked = buildSet { for (j in 0 until packages.length()) packages.optString(j).takeIf { it.isNotBlank() }?.let { add(it) } }
                    add(LocationRule(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        latitude = item.optDouble("lat", Double.NaN),
                        longitude = item.optDouble("lon", Double.NaN),
                        radiusMeters = item.optDouble("radius", 150.0).toFloat(),
                        blockedPackages = blocked
                    ))
                }
            }.filter { it.id.isNotBlank() && it.latitude.isFinite() && it.longitude.isFinite() }
        } catch (_: Exception) { emptyList() }
    }
    fun saveLocationRule(rule: LocationRule) {
        val all = locationRules().filterNot { it.id == rule.id } + rule
        val array = JSONArray()
        all.forEach { item ->
            val packages = JSONArray()
            item.blockedPackages.forEach(packages::put)
            array.put(JSONObject().put("id", item.id).put("name", item.name)
                .put("lat", item.latitude).put("lon", item.longitude)
                .put("radius", item.radiusMeters.toDouble()).put("packages", packages))
        }
        prefs.edit().putString(locationRulesKey(), array.toString()).apply()
    }
    fun removeLocationRule(id: String) {
        val array = JSONArray()
        locationRules().filterNot { it.id == id }.forEach { item ->
            val packages = JSONArray(); item.blockedPackages.forEach(packages::put)
            array.put(JSONObject().put("id", item.id).put("name", item.name)
                .put("lat", item.latitude).put("lon", item.longitude)
                .put("radius", item.radiusMeters.toDouble()).put("packages", packages))
        }
        prefs.edit().putString(locationRulesKey(), array.toString()).apply()
        setActiveGeofences(activeGeofences() - id)
    }
    fun activeGeofences(): Set<String> = prefs.getStringSet("active_geofence_ids", emptySet())?.toSet() ?: emptySet()
    fun setActiveGeofences(ids: Set<String>) { prefs.edit().putStringSet("active_geofence_ids", ids.toSet()).apply() }
    fun isLocationBlocked(packageName: String): Boolean {
        val active = activeGeofences()
        return locationRules().any { it.id in active && packageName in it.blockedPackages }
    }

    fun setFeature(key: String, enabled: Boolean) { prefs.edit().putBoolean("feature_$key", enabled).apply() }
    fun feature(key: String, default: Boolean = false): Boolean = prefs.getBoolean("feature_$key", default)

    companion object {
        fun normalizeHost(raw: String): String = raw.trim().lowercase()
            .removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore('?').removePrefix("www.")
    }
}
