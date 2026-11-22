# Development status — updated source snapshot

| Area | Status |
|---|---|
| Dashboard / navigation | Implemented; visual matching not pixel-identical |
| Installed app discovery / blocklist | Implemented |
| Basic whitelist mode | Implemented; device QA required |
| Per-app daily usage limits | Implemented using UsageStatsManager; limits are profile-scoped; permission and QA required |
| Foreground app blocking | Implemented using user-enabled Accessibility Service; QA required |
| Website host / keyword matching | Best-effort visible-text matching only |
| Reels / Shorts | Heuristic visible-label matching only; not reliable |
| Notification filtering | Listener is declared in manifest; user must enable Notification Access |
| Weekly schedule | Saved start/end/day settings, including overnight intervals |
| Scheduled notification filtering | Optional schedule-aware behavior |
| Strict mode | Prevents selected in-app rule edits; not tamper-proof |
| Profiles | App/site/keyword/notification blocklists, whitelist mode and allowed-app rules are isolated per active profile; legacy Default rules remain readable |
| Location / geofencing | Source implementation present; build/device QA required |
| Local VPN / DNS website filtering | Not implemented |
| Focus sounds / persistent strict alarm | Not implemented |
| Analytics: app launch counts, browser foreground time, keyguard unlock events, recent app timeline | Implemented using UsageStatsManager events; device/API validation required |
| PURESHIELD AI image scan | UI + ONNX inference integration added; model weights not bundled; selected-image scan only |
| Full 23-screen visual parity | Not complete |
| Android build / Android 16 physical-device QA | Not run in this environment |

Profile-isolation implementation is source-complete but has not been runtime-tested. Schedules, feature toggles and strict-mode state remain device-wide rather than profile-specific. Daily limits have now been moved to profile-scoped keys; legacy Default-profile limits remain readable for upgrade compatibility.

Analytics stage update: the Analytics screen now reads daily app-foreground events for launch counts and recent activity, estimates browser foreground time from UsageStats, and counts KEYGUARD_HIDDEN events where Android exposes them. These are device-reported usage events, not guaranteed exact counts; OEM/API behavior must be validated.

This is a development source snapshot, not a verified production release. Build with Android Studio, resolve any SDK/plugin issues, then test all permissions and blocking behavior on the target phone before relying on it.


Profile daily-limit stage update: each profile now reads and writes its own per-app daily limits. The Default profile can still read pre-update legacy `limit_<package>` preferences, while newly saved values use profile-scoped keys. Runtime switching and enforcement still require device QA.

Profile schedule stage update: weekly schedule enabled state, start/end minutes, and selected weekdays are now stored per profile. Existing device-wide schedule preferences remain readable as the Default profile migration fallback. This source change has not been compiled or runtime-tested on Android 16.

Location/geofencing stage update: added profile-scoped circular location rules (place name, latitude/longitude, 50–1000 m radius, selected app packages), foreground/background location permission flow, Google Play services GeofencingClient registration, enter/exit transition receiver, active-zone persistence, Accessibility-service enforcement for selected apps while inside an active zone, and geofence re-registration after device boot or app replacement. This is source-implemented only; Gradle/APK build and physical-device tests have not been run. Reliable background events require Fine Location + Background Location, device Location enabled, Google Play services, and OEM battery-policy validation. Location rules are scoped to the active profile and are re-synced when the Location Blocking screen is opened; changing profiles should be followed by opening that screen to refresh the registered geofences.


PURESHIELD AI milestone 1: added a dedicated screen under Modes, image selection using Android's system picker, local ONNX Runtime inference integration for the Apache-2.0 Marqo NSFW Image Detection 384 export, configurable auto-quarantine threshold, and app-private quarantine copies. The original selected image is not deleted. The actual `model.onnx` weights are not included in this ZIP; add them at `app/src/main/assets/models/nsfw-image-detection-384.onnx` using the model source and license details in `app/src/main/assets/models/README.txt`. Without that asset the app shows a model-missing state and does not pretend to scan. This model is binary NSFW/Safe only. Gallery-wide monitoring, automatic download scanning, video scanning, and cross-app screen scanning remain future stages. Gradle build and Android 16 device QA have not been run.

PURESHIELD AI source update (latest): added ONNX Runtime integration with dynamic model input/output names, explicit first-run model download over HTTPS with SHA-256 verification, app-private model storage, single-image scan, sampled-frame video scan, bounded recent MediaStore image/video scan, app-private quarantine copies, and optional 12-hour WorkManager background scanning. The model binary is not bundled; the user must download it once from the configured Hugging Face repository. The model is binary NSFW/Safe only, not four-class Suggestive/Nudity/Explicit. Video scanning samples frames and does not inspect audio. Quarantine preserves original media. Runtime permissions, download, inference, WorkManager behavior, and Android 16 device QA remain unverified. No APK build has been run.
