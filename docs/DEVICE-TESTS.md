# Physical acceptance checklist — not yet executed

Record model, `Build.FINGERPRINT`, HyperOS version, Android SDK, Shizuku version and setup method before testing. Never mark an item passed from a successful Binder response alone.

1. **Bounded hardware prototype:** test default cyan/500 ms. Both rings must illuminate and turn off. Repeat with red and white. Test 100 ms and 2000 ms durations. Reject confirmation if light is stuck or never visible.
2. **Finite fallback:** during a 2000 ms test, force-kill the app process (not system_server/Shizuku). Verify firmware extinguishes the ring. If it does not, disable reminders pending a backend fix.
3. **Single notification:** send one WhatsApp message. Leave it posted for at least 10 minutes, then dismiss. Repeated flashes should continue throughout and stop after removal (allowing callback/scheduling latency).
4. **Screen off:** repeat with display off for 30 minutes, precise timing on, battery optimization exempted. Measure cadence and battery drain. Repeat without exemption and with precise timing off to document delays rather than promise impossible timing.
5. **Multiple apps:** leave WhatsApp, Telegram and Google Messages notifications active. Dismiss one at a time. Flashes must continue until the last eligible child notification is removed.
6. **Updates/groups:** send several messages into one conversation and multiple conversations. Confirm updates do not add loops. Expand/collapse the group, remove one child, then clear all. A leftover group summary alone must not keep blinking.
7. **Unlock/shade:** unlock without removing notifications and open/close the shade. Flashes must continue. Reading a conversation stops reminders only if its app removes the notification.
8. **Live settings:** change interval from 5 to 1 to 30 seconds, color, duration and double flash while active. The old sequence must clear before the new one starts; no overlapping pulses. Interval denotes dark time after the sequence, including the 400 ms preparation before ON.
9. **Filtering:** disable each app with notifications posted. Count and reminders must reflect only enabled apps. Ongoing calls/connection notifications and summaries are excluded.
10. **Master disable:** turn off the main switch mid-flash and from the status notification action. LEDs must clear, the wake lock must release, and the status notification must disappear.
11. **Shizuku loss:** stop Shizuku mid-flash. There must be no command retry storm. Restart/authorize it; appropriate reminders should recover. Confirm the finite firmware timer clears the last pulse when explicit off is impossible.
12. **Listener reconnect:** revoke/regrant notification access with messages posted. No LEDs while disconnected; count is rebuilt from the current system snapshot on reconnect, not old counters.
13. **Reboot:** reboot with settings enabled. Restart non-root Shizuku and unlock as needed. Existing surviving notifications should be rediscovered when the listener reconnects. No claim of operation while Shizuku is stopped or before user unlock.
14. **HyperOS conflicts:** test while charging, receiving a call, with camera open, music lighting and native notification effects. Record overrides and disable competing effects manually if needed.
15. **Firmware update:** install an OS update. The stored visual confirmation must no longer match the new fingerprint; reminders remain gated until another visual test.
16. **UI/accessibility:** test light/dark themes, large font, TalkBack, narrow screen, status/permission actions and a long settings screen. UI rendering has not been verified on an emulator here.

Useful read-only diagnostics through an authorized ADB connection:

```text
adb shell getprop ro.product.model
adb shell getprop ro.build.fingerprint
adb shell getprop ro.build.version.sdk
adb shell service list
adb shell dumpsys activity services dev.persistentrings
adb shell dumpsys power
adb logcat -d -s AndroidRuntime ActivityManager
```

Share only relevant logs and remove unrelated personal data. The application deliberately never captures notification text. `dumpsys notification` can contain private content and is not required for the basic test.

## Color and pattern checks

- Test pure red (#FF0000), yellow, green, cyan, blue, magenta and white center against the displayed hex. Confirm changes remain local while dragging, and a completed gesture updates the active reminder.
- Test Single flash and Double flash using **Test selected color & pattern**. Switch patterns mid-cycle and confirm that the old slot clears before the new pattern starts.
- Assign blue and green to two apps and post a notification from each. Check newest-first order, OFF and the 400 ms inter-app pause, and the configured dark interval after the last app. Post another notification to reorder the cycle. Dismiss one app and confirm only the remaining app continues.
- Change a custom color and the default while reminders run. Use **Test this app color** before saving, deselect and reselect an app, then remove its override with **Use default color**.
- Upgrade an old install with double flash enabled and disabled: confirm migration and retention of color, interval, access and application settings.

## Version 1.2.0 physical checks

- Upgrade the supplied v1.1.0 APK in place. Confirm that the saved packages and lighting settings survive. On a fresh install, check that no packages are selected.
- Pick an app outside the original three and post a notification. Search by label and package, toggle system visibility, and confirm that a headless app with an active notification can be selected.
- Revoke and restore notification listener access. Check the setup card, the notification detail link and that reminders require access. Approve or reject the Android battery exemption dialog and check the reported status on return. Check the HyperOS autostart link and App info fallback on the phone.
- Enable face-down-only mode and leave an eligible notification posted. Keep the phone face up, turn it face down, then lift and tilt it. Check that the LEDs start after face-down confirmation and clear after exit confirmation. Repeat with the screen off, including a period of CPU sleep, to check wake-up sensor delivery on this firmware.
- Dismiss the last selected notification while the LEDs are active or while waiting for orientation. Check that LEDs clear, the foreground status disappears and the sensor stops. Check that the manual test still works face up.
