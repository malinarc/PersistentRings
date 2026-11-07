# Attribution

The Binder camera-ring protocol, finite-duration cleanup and clear/settle workaround were informed by [Aura](https://github.com/tgvdufuture/Aura), revision `ba8ff489a1de9326f0827fe452a925ee27c9d2a0`, MIT, copyright (c) 2026 Aura Contributors. Its MIT notice is retained in LICENSE. Gradle launchers and wrapper JAR were sourced from that project's Gradle wrapper; Gradle is Apache-2.0 licensed (launcher license headers are retained).

[HaloLight](https://github.com/fan-world-me/HaloLight), revision `73506bb9b8c90ca587c88fee2a75a775865066e6`, GPL-3.0, was consulted as hardware/protocol research (API, Binder and brightness documents). Its application implementation and GPL source files are not included in this project. Research citations and the selected protocol facts are documented in `docs/HARDWARE.md`.

Runtime/build dependencies are retrieved by Gradle, not vendored in this source delivery. AndroidX/Compose/DataStore, Kotlin, kotlinx.coroutines, Shizuku and Gradle retain their respective upstream licenses and notices. Shizuku source: https://github.com/RikkaApps/Shizuku-API . JUnit 4 tests use JUnit under EPL-1.0. See each resolved artifact's metadata/license files before redistribution of a packaged APK.
