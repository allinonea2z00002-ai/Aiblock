package com.focusguard.app.service

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.data.BlockStore
import java.util.Calendar

/** Local, user-enabled foreground restriction service. Not a tamper-proof kiosk. */
class FocusAccessibilityService : AccessibilityService() {
    private var lastBlockedPackage: String? = null
    private var lastRedirectAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val store = BlockStore(this)
        val foregroundPackage = event.packageName?.toString() ?: return
        if (foregroundPackage == packageName || foregroundPackage == "com.android.systemui") {
            lastBlockedPackage = null
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val scheduleApplies = !store.scheduleEnabled() || store.isWithinSchedule()
        val protectedPackages = setOf(
            packageName, "com.android.systemui", "com.android.settings",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "com.android.phone", "com.android.dialer", "com.google.android.dialer"
        )
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homePackage = packageManager.resolveActivity(homeIntent, 0)?.activityInfo?.packageName
        val isProtected = foregroundPackage in protectedPackages || foregroundPackage == homePackage
        val appBlocked = scheduleApplies && !isProtected && if (store.isWhitelistMode()) {
            foregroundPackage !in store.allowedApps()
        } else {
            foregroundPackage in store.blockedApps()
        }
        val limit = store.dailyLimitMinutes(foregroundPackage)
        val dailyLimitReached = limit > 0 && usedToday(foregroundPackage) >= limit * 60_000L
        val visibleText = buildString {
            event.text?.forEach { append(it).append(' ') }
            event.contentDescription?.let { append(it).append(' ') }
            appendVisibleText(rootInActiveWindow, this, 0)
        }.lowercase()
        val siteBlocked = scheduleApplies && store.blockedSites().any { it.isNotBlank() && visibleText.contains(it) }
        val keywordBlocked = scheduleApplies && store.keywords().any { it.isNotBlank() && visibleText.contains(it) }
        val reelsBlocked = scheduleApplies && isDistractingFeed(foregroundPackage, visibleText, store)
        val locationBlocked = !isProtected && store.isLocationBlocked(foregroundPackage)
        val shouldBlock = appBlocked || dailyLimitReached || siteBlocked || keywordBlocked || reelsBlocked || locationBlocked

        val now = System.currentTimeMillis()
        if (shouldBlock && (lastBlockedPackage != foregroundPackage || now - lastRedirectAt > 1800L)) {
            lastBlockedPackage = foregroundPackage
            lastRedirectAt = now
            packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
            }
        } else if (!shouldBlock) {
            lastBlockedPackage = null
        }
    }

    private fun isDistractingFeed(pkg: String, text: String, store: BlockStore): Boolean {
        val rules = listOf(
            "com.google.android.youtube" to ("yt_shorts" to listOf("shorts", "shorts shelf")),
            "com.instagram.android" to ("ig_reels" to listOf("reels", "reel")),
            "com.facebook.katana" to ("fb_reels" to listOf("reels", "reel")),
            "com.snapchat.android" to ("snap_spotlight" to listOf("spotlight"))
        )
        val rule = rules.firstOrNull { it.first == pkg } ?: return false
        return store.feature(rule.second.first) && rule.second.second.any { text.contains(it) }
    }

    private fun appendVisibleText(node: AccessibilityNodeInfo?, out: StringBuilder, depth: Int) {
        if (node == null || depth > 18) return
        node.text?.let { out.append(it).append(' ') }
        node.contentDescription?.let { out.append(it).append(' ') }
        for (i in 0 until node.childCount.coerceAtMost(60)) {
            appendVisibleText(node.getChild(i), out, depth + 1)
        }
    }

    private fun usedToday(pkg: String): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val manager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        return manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
            ?.filter { it.packageName == pkg }?.sumOf { it.totalTimeInForeground } ?: 0L
    }

    override fun onInterrupt() = Unit
}
