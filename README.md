# Persistent Rings — POCO X8 Pro

Kotlin / Jetpack Compose / Material 3 Android project for rear camera-ring reminders while notifications from selected apps remain active. It uses Preferences DataStore, NotificationListenerService and Shizuku Binder access. The minimum and target platform is Android 16 (API 36).

## Version 1.3.0 / versionCode 4 — 2026-11-06

The project uses AGP 8.13.2, Kotlin 2.2.21, Gradle 8.13, JDK 17 and SDK 36. The debug APK builds to `app/build/outputs/apk/debug/app-debug.apk`. See `verification/BUILD-STATUS.md` for the version, signature and test record.

Verify the rear-ring color cycle, face-down detection and settings links on the physical POCO X8 Pro. Visually confirm the hardware test before enabling reminders.

### Installed applications and orientation

Open **Monitored applications → Choose installed apps**. Search by label or package name. **Select all** adds the displayed results and **Clear all** removes every selection. Enable **Show system apps** to include system packages without a launcher or current notification. The picker also includes apps observed in active notifications, including apps with no launcher. New installs start with an empty selection. Updates retain the stored selection.

This personal sideload build requests `QUERY_ALL_PACKAGES` to show installed apps. Google Play restricts this permission and requires an eligible core use case and declaration. [Google Play package visibility policy](https://support.google.com/googleplay/android-developer/answer/10158779)

**Only remind when the phone is face down** uses a gravity sensor or filtered accelerometer while eligible notifications remain and the foreground reminder service is active. It prefers wake-up sensors for screen-off detection, then gravity within each category. Entry requires normalized Z at or below −0.82 for 500 ms. Exit requires Z at or above −0.65 for 500 ms, then cancels the LED session and sends OFF. The foreground status stays visible while waiting. The partial wake lock runs during LED sessions when precise timing is enabled, and during bounded manual tests. Non-wake-up sensor detection can be delayed while the CPU sleeps. [Android sensor guidance](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)

### Color and patterns

**Default LED color** applies to apps without an override. In **Choose installed apps**, each row shows its resolved color and whether it is custom or inherited. Choose **Set app color**, edit the HSV preview and tap **Save app color**. **Select pure red** chooses exactly #FF0000. **Use default color** removes the override. Deselecting an app preserves its override. The per-app editor saves on confirmation and **Test this app color** flashes the draft once with the selected pattern in any orientation. The default wheel saves when a gesture ends.

| Pattern | Commands / duration |
|---|---|
| Single flash | Mode 0, selected pulse duration, explicit off |
| Double flash | Two mode-0 pulses, 400 ms dark pause, explicit off |
Each active package gets one slot, ordered by newest eligible notification first and then package name. Each slot plays its pattern and sends OFF. A 400 ms pause separates apps. The configured dark interval follows the last slot before the cycle repeats.

DataStore preserves the selected package set and default color. Overrides use versioned package/RGB entries. Pattern migration preserves Single flash and Double flash, and maps retired or invalid values to Single flash. The older `double` Boolean still migrates when a pattern string is absent.

### Install or update

Transfer `app/build/outputs/apk/debug/app-debug.apk` to the phone and open it, or run `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Updating preserves settings only if the existing app has the same signing key. If Android reports a signing conflict, rebuild with the original key; uninstalling instead would erase the old local settings. Do not uninstall automatically.

After installation, start Shizuku, grant this app access, enable notification access and the HyperOS rear lights, then test the selected color/pattern visually. Follow the background settings and physical checklist below. No hardware success is inferred from a successful Binder reply.

## Build in Android Studio

1. Open this directory as an Android Studio project. Use an Android Studio release supporting AGP 8.13.2, with JDK 17 as its Gradle JDK.
2. In SDK Manager install Android SDK Platform 36 and Android SDK Build-Tools 35.0.0 (AGP selects a compatible version). Accept the SDK licenses.
3. Let Android Studio create `local.properties` with your SDK location. Do not commit that machine-specific file.
4. Sync Gradle, then run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --stacktrace
```

On Linux/macOS: `chmod +x gradlew`, then `./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace`.

The resulting debug-signed APK is `app/build/outputs/apk/debug/app-debug.apk`. Install with Android Studio Run, or:

```text
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The provided `.github/workflows/build.yml` can build/test/package it on GitHub Actions if you upload this project to a repository and run the workflow. That workflow has **not** been run here. No source has been published or uploaded on your behalf.

## Phone setup and hardware prototype

1. Install Shizuku from its official distribution. Enable Developer options, then Wireless debugging, and follow Shizuku's pairing/start instructions: https://shizuku.rikka.app/guide/setup/ . Keep Shizuku running. No root is required. ADB-started Shizuku does **not** automatically survive reboot: restart it after each reboot. Xiaomi may require Developer options → USB debugging (Security settings) for some privileged operations; follow Shizuku's device-specific guidance if access fails.
2. Install and open Persistent Rings. Tap **Grant Shizuku access**, approve in Shizuku, and verify the app shows authorized.
3. In HyperOS Settings → Additional settings → Back light effects (wording varies), enable rear lighting. Check any lighting schedule. If native notification/music/game lighting interferes, disable those effects manually. This app does not change global lighting settings or disable other effects automatically.
4. Tap **Test selected color & pattern**. It sends a clear, waits 400 ms, plays the selected pattern once and sends OFF. Observe both rings and tap **Yes, confirmed** only if they flash and turn off. If they remain dark, tap **No** and follow `docs/HARDWARE.md`.
5. Grant notification access for Persistent Rings. If Android blocks it because the app was sideloaded, App info → ⋮ → Allow restricted settings, then return to Notification access. The OS may not offer this option on every firmware/distribution.
6. Allow this app's status notification. Tap **Request unrestricted battery operation** and approve the Android system dialog if you want a battery optimization exemption. The checklist reads the actual exemption status. Set HyperOS **No restrictions** and **Background autostart** yourself. The autostart button opens a Xiaomi settings page when the phone resolves it and falls back to App info. [Android battery exemption](https://developer.android.com/reference/android/provider/Settings#ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) · [Xiaomi autostart guide](https://www.mi.com/global/support/faq/details/KA-507611/)
7. Select installed apps and enable **Persistent reminders**. Post a notification from one selected app and follow `docs/DEVICE-TESTS.md`.

## What the app does

- Tracks notification **keys**, not callback counts. Each event schedules a conflated full active-notification snapshot. Reconnect replaces stale state from the system's current notifications.
- Reads metadata snapshots from all packages Android exposes to the notification listener and matches them against the saved package set. Work profile visibility depends on Android and administrator policy.
- Excludes group summaries and ongoing notifications. Child notifications are counted individually. A summary left behind cannot keep a reminder running forever. If an app publishes **only** a summary and no eligible child, it is intentionally not monitored in this implementation.
- Treats notification presence as a proxy for unread state. It cannot determine whether a message is actually read inside an app. If an app leaves a notification posted after a message is read, reminders continue until that notification is removed.
- Uses one `collectLatest` session. Changes to eligible notification metadata, settings or the orientation gate cancel the previous cycle and wait for LED cleanup before starting its replacement. Identical snapshots keep the current cycle.
- Does not listen for screen unlock or notification shade events. Those events never stop reminders. Removal of the last eligible notification, disabling the app, access loss, listener disconnect, or an LED error stops the session.
- Interval is **dark time after a flash sequence**, adjustable 1–30 seconds, default 5. Duration is 100–2000 ms, default 500. Settings apply on slider release and are saved through DataStore. Single and double pulse patterns are software-scheduled finite flashes, not undocumented hardware pattern IDs.
- Uses a `specialUse` foreground session on the system-bound listener while reminding, with an ongoing status notification and disable action. Foreground startup errors are shown; the app does not bypass OS restrictions. Google Play distribution would require review of the declared special-use case.
- Precise screen-off timing holds a partial wake lock only during reminders (and bounded tests). Its 120-second timeout is renewed by LED commands, and cleanup releases it. This keeps the CPU awake between flashes and costs battery. Turning it off permits delayed flashes during sleep. There is no wake lock, polling timer, exact alarm or blinking job when idle.
- Boot requests a framework listener rebind. State is recovered from current notifications, not persisted stale counts. Shizuku privilege must return before LEDs resume. Force-stop prevents recovery until the app is opened again. Recovery before the user's first unlock is not promised.
- Stops and reports hardware errors rather than issuing an endless stream of failing commands. Fix the cause, then tap **Refresh access / retry reminders**. Binder death and permission-result events refresh access automatically.

## Hardware limits

The only implemented backend is community-verified `miui.lights.ILightsManager` transaction 4, camera style 12, through Shizuku. It is undocumented and firmware-specific. No flashlight simulation, no root fallback, no raw sysfs writes, no unverified notification-manager transaction numbers.

Both rings are controlled together. There is no exposed per-ring selector in this verified method. Brightness is a **global HyperOS setting**; the research documents that writing it produces a white preview and delayed off that can interrupt effects. The UI therefore links to system lighting settings rather than presenting a false per-flash brightness slider. `brightnessMode` is not brightness intensity.

See `docs/HARDWARE.md` for source revisions, exact wire contract, limitations and alternatives investigated. See `docs/ARCHITECTURE.md` for source navigation. No message content is stored, no analytics are included, and the app requests no Internet permission.
