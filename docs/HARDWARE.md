# Stage 1 research and Stage 2 physical gate

Sources were inspected as source repositories, not just search summaries, on 2026-11-01.

| Source | Pinned revision / evidence |
|---|---|
| [Aura](https://github.com/tgvdufuture/Aura) | `ba8ff489a1de9326f0827fe452a925ee27c9d2a0`; MIT; `app/src/main/java/com/aura/led/led/Led.kt` implements transaction 4, caller string, camera scene, clear/settle and finite onMs. Its README states development/testing on X8 Pro HyperOS 3 / Android 16. |
| [HaloLight API research](https://github.com/fan-world-me/HaloLight/blob/73506bb9b8c90ca587c88fee2a75a775865066e6/docs/API.md) | `73506bb9b8c90ca587c88fee2a75a775865066e6`; describes framework decompilation and camera-ring calls on X8 Pro (`klee`), HyperOS 3 / SDK 36. |
| [HaloLight Binder research](https://github.com/fan-world-me/HaloLight/blob/73506bb9b8c90ca587c88fee2a75a775865066e6/docs/BINDER.md) | Service registration, caller checks and Awinic AW21024 hardware. |
| [HaloLight brightness research](https://github.com/fan-world-me/HaloLight/blob/73506bb9b8c90ca587c88fee2a75a775865066e6/docs/BRIGHTNESS.md) | `Settings.Global` brightness key and 5-second preview/off side effects. |
| [Xiaomi POCO X8 Pro FAQ](https://www.mi.com/ng/support/faq/details/KA-676361/) | Initial OS is HyperOS 3 / Android 16. |
| [Android platform SDK release notes](https://developer.android.com/tools/releases/platforms) | API 36 stable platform; informs compile/target SDK selection. |

These are upstream research claims, **not tests performed on the user's phone**. No firmware fingerprint from the user's device was obtained here. Compatibility with that firmware is unresolved until a physical test passes.

## Selected interface

Service name and descriptor: `miui.lights.ILightsManager`. It is distinct from the AOSP `lights` service and the raw light HAL. Obtain with Shizuku `SystemServiceHelper`, wrap with `ShizukuBinderWrapper`, check the descriptor, and transact synchronously with code **4**. The Parcel layout is:

1. Interface token `miui.lights.ILightsManager`.
2. RGB color int (low 24 bits; zero clears).
3. `flashMode = 0` (solid finite pulse; no presumed hardware blink semantics).
4. `onMs = 100..2000`, chosen flash duration.
5. `offMs = 0`.
6. `brightnessMode = 0` (mode, not an intensity).
7. Package string **`com.android.camera`**, required by the researched `checkCustomLightCaller` firmware path. This is an explicit firmware caller-string quirk, not this app's installed identity.
8. `styleType = 12` (camera).
9. `userId = 0` (owner profile).

Check the transact return value and `reply.readException`, always recycle both Parcels, and surface failures. Service presence and an accepted transaction cannot establish that a ring illuminated. The finite `onMs` behavior and explicit off also need confirmation on the actual firmware.

The engine clears and waits **400 ms** before a sequence; Aura documents that back-to-back clear/color can be dropped on an X8 Pro build and that roughly 350 ms spacing worked there. Each pulse explicitly turns off and a double flash leaves another 400 ms settle gap. There is no continuously latched ON command. This may still require timing adjustments on another firmware; the physical test is mandatory.

## Alternatives investigated

- **Standard flashlight / CameraManager:** controls the torch, not the rear RGB rings. Not used.
- **AOSP LightsManager / raw HAL:** not interchangeable with Xiaomi's service; the researched ring is absent from the standard app-accessible lights. Not used.
- **`setColorCommon`, transaction 2:** HaloLight's app uses style 3 for a music-strip color animation. The newer API research distinguishes that from the camera path and documents style-dependent no-ops. Transaction 4 is selected because both Aura and the camera-ring research support it, and it offers finite durations.
- **`setColorLed`, transaction 3:** research reports an empty server implementation on the inspected build. Not used.
- **Hardware timed blinking modes:** values are partly inferred, not sufficiently proven for an indefinite reminder on this firmware. Software finite pulses are used instead.
- **Direct AW21024 sysfs:** research identifies the Awinic driver, but SELinux blocks shell access even through Shizuku. Root/sysfs access would be a different, riskier backend and is not implemented.
- **NotificationManager hidden Binder getter:** research mentions `getColorLightManager()` and a build-specific transaction number, but explicitly says end-to-end non-Shizuku operation was untested. Not shipped as a guessed fallback.
- **Independent ring addressing:** no ring-index parameter in the verified interface. Not exposed.
- **Global brightness write:** possible in researched HyperOS settings, but globally mutates system behavior and triggers a white preview/off. Kept in the native UI instead of silently writing it.

If the descriptor/service is absent or the visual test fails, stop here. Record model, Android version, HyperOS build and diagnostic output, compare the exact firmware to upstream research, and inspect its actual framework before proposing another wire contract. Do not cycle arbitrary transaction IDs or claim a successful call means hardware support.

## Android background references

- [NotificationListenerService contract](https://developer.android.com/reference/android/service/notification/NotificationListenerService): wait for connection; use current active notifications; requestRebind is safe after disconnect.
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types): specialUse permission and subtype declaration.
- [Background foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start): startup can be denied; battery exemptions and user interaction affect eligibility.
- [Shizuku setup](https://shizuku.rikka.app/guide/setup/): wireless/ADB startup and device-specific constraints.

Single flash and Double flash use finite pulses through this interface. Each package slot uses its resolved RGB color. The engine sends OFF and waits 400 ms before the next package slot.
