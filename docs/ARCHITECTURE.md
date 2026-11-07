# Source map and ownership

`MainActivity` is a Compose Material 3 screen with lifecycle-aware Flow collection. `MainViewModel` owns UI queries and actions, while `SettingsStore` serializes DataStore edits. `RingSettings` is the immutable configuration model.

`RingsListener` is system-bound under notification access. It coalesces event bursts into an authoritative snapshot and passes key, package, post time and summary/ongoing flags in memory. It also enters the started state and foreground while a reminder session needs it. Foreground startup failures stop the session and show an error.

`RingsRuntime` calculates eligibility from one combined snapshot of settings and notifications. It checks notification access, listener connection, firmware confirmation and Shizuku authorization before arming the foreground service. The foreground service stays active while eligible face-down reminders are waiting for sensor events. The sensor listener is released on idle, disarm or mode change.

A single `collectLatest` chain cancels and joins the previous LED session before the next can touch LEDs. The request includes the canonical eligible snapshot and settings. It groups by package and sorts by latest post time descending, then by package name. Manual tests bypass orientation and temporarily replace the reminder with one slot.

`InstalledAppsRepository` loads package metadata on IO and fetches icons only for visible picker rows, with a bounded cache. The catalog excludes disabled packages and this app, sorts by localized label and includes packages observed in notifications. DataStore preserves arbitrary saved package names without filtering them through the current catalog.

`BlinkEngine` sends finite pulses in sequence, with explicit OFF after each pulse, 400 ms between packages, and the configured dark interval after the last package. The finally block sends OFF under `NonCancellable`. Finite ON duration provides a fallback if cleanup cannot reach the service.

`HyperOsHardware` is the single Binder adapter, running transactions on the IO dispatcher. It requires Shizuku authorization, resolves the service afresh (avoiding cached dead wrappers), checks its interface descriptor, checks `transact`, reads remote exceptions and recycles Parcels. It never guesses alternative transaction numbers.

On idle/disconnect/disable, cancellation removes the foreground status, releases the active-only bounded wake lock and stops further LED commands. A force-killed process cannot perform finally cleanup, so hardware finite-duration behavior is a required acceptance test. No database of old unread notifications is restored after reboot; current state comes from the framework.

JVM tests cover package grouping, color override persistence, ordering and slot timing, face-down thresholds, and serialized OFF cleanup after settings changes or notification removal. Disk tests use OkioStorage for atomic file replacement on Windows. The UI, screen-off sensor delivery and rear-ring effects require device testing.
