# ScrollWatcher

Personal doom-scroll budget for Android — a daily **30-minute** cap across apps you mark as “evil” (social feeds, etc.), with a full-screen overlay lockout when the budget is exhausted.

**No AccessibilityService.** Foreground detection uses `UsageStatsManager` / usage events only.

## Features

- List launchable installed apps and toggle “evil” targets
- While the screen is on and an evil app is in the foreground, accumulate usage time
- Daily base budget: **30 minutes** (device local midnight reset)
- Buy / add **+10 minutes** of extra time for the current day only
- When remaining time ≤ 0 and an evil app is foreground → `SYSTEM_ALERT_WINDOW` overlay lockout
- Foreground service with a low-importance persistent notification
- Restarts monitoring after reboot (`RECEIVE_BOOT_COMPLETED`)
- Persists evil list, used time, extras, and last reset date via **DataStore Preferences**

## Requirements

- Android 8.0+ (API 26), target / compile SDK 35
- Kotlin 2.4 / AGP 9.4 / Gradle 9.6, Jetpack Compose, Material 3
- Special permissions (user must grant in system settings):
  - **Usage access** (`PACKAGE_USAGE_STATS`)
  - **Display over other apps** (`SYSTEM_ALERT_WINDOW`)
  - **Notifications** (Android 13+)
  - Foreground service (declared; special-use type)

## Build (Android Studio)

1. Clone this repo and open the root folder in **Android Studio** (Hedgehog / Ladybug or newer recommended).
2. Let Gradle sync; install any prompted SDK components (API 35 platform, build-tools).
3. Connect a device or start an emulator (API 26+).
4. Run **app** (Debug).

Command line (with Android SDK + `ANDROID_HOME` configured):

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Sideload / install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then open ScrollWatcher → **Permissions** and grant:

1. Usage access → enable ScrollWatcher  
2. Display over other apps → allow  
3. Notifications (if prompted)

Mark evil apps under **Evil apps**, leave **Monitoring** on, and use the Home **+10 min** button when you need extra time.

## How tracking works

- A foreground service polls ~every 1.5s while monitoring is enabled.
- Only counts time when the screen is interactive (`PowerManager.isInteractive` / screen on) **and** the current foreground package (from `UsageEvents`) is in your evil set.
- Daily reset: on each tick and on app open, if the calendar date (device timezone) changed, used minutes and same-day extras are zeroed; the evil-app list is kept.
- Remaining = `(30 min + extras today) − used`. Overlay shows when remaining ≤ 0 and an evil app is foreground.

## Play Store caveats (later)

This app uses sensitive permissions (`PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, `QUERY_ALL_PACKAGES`, foreground service special use). Google Play requires declarations, justifications, and often restricted approval. Sideloading / personal use is the intended first path; do not publish without completing Play Console policy forms.

## Architecture

```
app/src/main/java/com/juliensalinas/scrollwatcher/
  ui/          Compose screens (Home, Evil apps, Permissions)
  service/     Foreground monitor, boot & screen receivers
  data/        DataStore preferences + models
  tracking/    UsageStats foreground detection + permission helpers
  overlay/     SYSTEM_ALERT_WINDOW lock UI
```

## License

Personal / open source — use and modify as you like.
