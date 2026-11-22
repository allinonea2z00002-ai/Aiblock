# Focus Guard — Android source project

Focus Guard is an independent Android focus/blocking app prototype based on the supplied 23 design-reference screenshots. It is not the original Stay Focused app and does not contain its proprietary source code or assets.

## Stack
- Kotlin, Jetpack Compose, Material 3
- Android Accessibility Service (explicit user enablement required)
- UsageStatsManager (explicit Usage Access required)
- NotificationListenerService (explicit Notification Access required)
- SharedPreferences for local settings
- Android SDK 35 / min SDK 26 / Java 17

## Included in this source snapshot
- Dashboard and Dashboard / Modes / Analytics / Account navigation
- Installed launchable app discovery and search
- App blocklist and a basic whitelist mode
- Per-app daily usage limit presets, isolated by profile (legacy Default limits remain readable)
- Accessibility-based foreground-app blocking
- Website host and keyword matching against text exposed by the active accessibility window
- Basic Reels/Shorts label heuristics for selected social apps (not guaranteed; app UI changes can break labels)
- Notification filtering for packages selected by the user, after Notification Access is explicitly enabled
- Weekly schedule storage and schedule-aware blocking
- Strict-mode setting that prevents in-app removal of existing rules
- Usage Access-based daily screen-time summary, per-app launch counts, browser foreground-time estimate, keyguard unlock-event count (where exposed by Android), and recent app activity timeline
- Pomodoro and break timers
- Profile-isolated app/site/keyword/notification blocklists, whitelist rules, per-app daily limits, weekly schedules, and location rules (strict mode remains global)
- Circular location geofences using Google Play services: configure a named zone by latitude/longitude, choose a 50–1000 m radius and selected apps, then block those apps while inside the active zone; enter/exit state is persisted and zones are re-registered after device boot/app replacement
- PURESHIELD AI first milestone: user-selected image scanning through an offline ONNX Runtime classifier, configurable auto-quarantine threshold, and app-private quarantine copies (original files are never deleted)
- Original 23 user-provided design references in `design-reference/`

## Important limitations — not release-ready
This project has **not** been built or tested on a physical Android 16 device in this environment. No Android SDK or Gradle executable is available here, so a successful APK build cannot be claimed.

The following still need implementation and device validation:
- PURESHIELD AI currently has the image-scan UI and ONNX inference integration, but the model weights are not bundled. Add the Apache-2.0 `model.onnx` from `https://huggingface.co/KanariKanaru/nsfw-image-detection-384-onnx` as `app/src/main/assets/models/nsfw-image-detection-384.onnx` before scanning can run. It is a binary NSFW/Safe classifier, not separate suggestive/nudity/porn classes. This first milestone scans only a user-selected image; whole-gallery, download monitoring, video and cross-app screen scanning are not implemented. Flagged selections are copied into app-private quarantine; the original remains untouched.
- Location/geofence source implementation is present, but has not been built or physically validated. It requires Fine Location and Background Location permissions, device Location enabled, Google Play services, and battery-policy testing. The active profile’s geofences are refreshed when opening Location Blocking; after switching profiles, open that screen to sync the new profile. Geofence delivery can be delayed by Android/OEM background limits.
- Website blocking is best-effort only. Accessibility does not expose every browser URL or page; a local VPN/DNS filter is needed for broader coverage.
- Reels/Shorts detection uses visible text labels and is not reliable across app versions, languages, or screens.
- Notification filtering works only after the user enables Notification Access; OEM restrictions and grouped notifications require device QA.
- Profiles isolate app/site/keyword/notification rules, whitelist settings, per-app daily limits, and weekly schedules. Strict mode remains global.
- The schedule UI saves a weekly time range, but background reliability depends on Android battery and execution policies.
- Strict Mode is not tamper-proof. Android users can revoke permissions, force-stop, clear data, uninstall, or use system-level bypasses. Stronger kiosk enforcement requires managed-device / Device Owner provisioning and should not be represented as an unbreakable consumer lock.
- Exact pixel-level matching of all 23 screenshots is not complete.
- Analytics are based on Android UsageStats/UsageEvents. Launch and unlock counts can vary by Android version/OEM; browser foreground time is not exact website browsing time. Validate on the target device.

## Run
1. Open the `FocusGuard` directory in a recent Android Studio version.
2. Install the Android SDK platform/build tools requested by Gradle.
3. Sync and build the project.
4. Install on a physical device.
5. Enable Accessibility, Usage Access, and (if needed) Notification Access from the in-app Settings links / Android Settings.
6. Test blocking and battery behavior on the target device.

## Privacy
Rules are stored locally using SharedPreferences. The app does not upload notification contents or accessibility text to a server. The Accessibility service reads visible UI text locally only to compare against user-configured block rules.

## Project identity
- App label: Focus Guard
- Application ID: `com.focusguard.app`

## PURESHIELD AI — added source implementation
- On-device ONNX Runtime image classifier integration with a first-run model download action.
- Model is downloaded over HTTPS once, checked against a pinned SHA-256, and stored in app-private storage; inference thereafter is offline.
- Manual single-image scan and single-video scan (sampled frames).
- Recent Gallery image/video batch scan using MediaStore, with media permission requests.
- Optional 12-hour WorkManager scan for newly added recent media after the user enables it.
- Flagged media is copied to app-private quarantine. Original media is deliberately retained; the app does not silently delete user files.
- Model: `kasimtmc/nsfw-image-detection-384-onnx`, quantized ONNX, Apache-2.0. See `app/src/main/assets/models/README.txt` for URL, input/output contract and SHA-256.

### PURESHIELD AI limitations
- The ONNX model is a binary NSFW/Safe classifier. It does not independently classify Suggestive, Nudity and Explicit categories.
- The model binary is downloaded on first use (internet required once); after successful checksum verification, model inference runs locally/offline. The model binary is not bundled in this source archive.
- Video detection samples frames rather than examining every frame; audio is not analyzed. Background scans inspect a bounded recent-media batch and only the first two minutes of each video.
- Android media access depends on the permissions the user grants. On Android 14+, selected-photo access may limit which items are visible to the scanner.
- Quarantine is an app-private copy, not removal of the original from Gallery. A flagged original remains where it was; no files are silently deleted.
- Model scores are not guaranteed probabilities. Swimwear, art and other borderline images can be false positives; explicit material can be missed. Validate thresholds on representative local data.
- This update has not been built with Android Gradle Plugin or tested on a physical Android 16 device in this environment.
