package dev.persistentrings

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import rikka.shizuku.Shizuku

class RingsApp : Application() {
    lateinit var runtime: RingsRuntime
        private set
    override fun onCreate() { super.onCreate(); runtime = RingsRuntime(this) }
}

private data class ReminderPrerequisites(
    val selected: SelectedNotifications,
    val connected: Boolean,
    val access: Boolean,
    val bridge: BridgeStatus
) {
    val settings get() = selected.settings
    val active get() = selected.apps.isNotEmpty()
    val armed: Boolean get() = settings.enabled && active && connected && access &&
        settings.verifiedFirmware == Build.FINGERPRINT && bridge.authorized
}

private data class ManualTest(val color: Int?)

private data class RunRequest(
    val prerequisites: ReminderPrerequisites,
    val foreground: Boolean,
    val orientationAllowed: Boolean,
    val test: ManualTest?,
    val retry: Int
)

class RingsRuntime(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store = SettingsStore(app)
    val settings = store.settings.stateIn(scope, SharingStarted.Eagerly, RingSettings())
    val notices = MutableStateFlow<List<Notice>>(emptyList())
    val connected = MutableStateFlow(false)
    val notificationAccess = MutableStateFlow(hasNotificationAccess())
    val foregroundActive = MutableStateFlow(false)
    internal val orientation = DeviceOrientationMonitor(AndroidOrientationSource(app))
    val hardware = HyperOsHardware()
    val bridge = MutableStateFlow(hardware.status())
    val message = MutableStateFlow("Ready. Set up access, then test the rear rings.")
    val running = MutableStateFlow(false)
    private val manualTest = MutableStateFlow<ManualTest?>(null)
    val testing = manualTest.map { it != null }.stateIn(scope, SharingStarted.Eagerly, false)
    val testSent = MutableStateFlow(false)
    private val retry = MutableStateFlow(0)
    // Bound listener controls its foreground notification; no arbitrary background FGS start.
    var listener: RingsListener? = null
    private val selection = selectedNotifications(settings, notices)
        .stateIn(scope, SharingStarted.Eagerly, SelectedNotifications(RingSettings()))
    val count = selection.map { it.count }.stateIn(scope, SharingStarted.Eagerly, 0)
    private val received = Shizuku.OnBinderReceivedListener { scope.launch { refreshBridge() } }
    private val dead = Shizuku.OnBinderDeadListener { scope.launch { refreshBridge() } }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> scope.launch { refreshBridge() } }
    init {
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        val prerequisites = combine(selection, connected, notificationAccess, bridge) { selected, bound, access, bridgeStatus ->
            ReminderPrerequisites(selected, bound, access, bridgeStatus)
        }
        scope.launch {
            // Keep the foreground status while waiting for orientation so Android will deliver
            // continuous sensor events. LED sessions and their wake locks are separate.
            combine(prerequisites, retry) { p, attempt -> if (p.armed) attempt else null }
                .distinctUntilChanged().collectLatest { attempt ->
                    if (attempt == null) return@collectLatest
                    val service = listener ?: return@collectLatest
                    if (!hasNotificationAccess()) {
                        refreshNotificationAccess()
                        return@collectLatest
                    }
                    try {
                        service.beginForeground()
                        foregroundActive.value = true
                        awaitCancellation()
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        message.value = "Could not start reminder service: ${error.message ?: error.javaClass.simpleName}. Open the app and tap Retry."
                    } finally {
                        foregroundActive.value = false
                        service.endForeground()
                    }
                }
        }
        scope.launch {
            orientation.track(combine(selection, foregroundActive) { selected, foreground ->
                selected.needsOrientation(foreground)
            })
        }
        scope.launch {
            combine(prerequisites, foregroundActive, orientation.state, manualTest, retry) { p, foreground, position, test, attempt ->
                RunRequest(p, foreground, test != null || p.settings.allowsOrientation(position), test, attempt)
            }
                .distinctUntilChanged().collectLatest { request ->
                    val p = request.prerequisites
                    val s = p.settings
                    val isTest = request.test != null
                    if (!isTest && !p.armed) {
                        if (!testSent.value) message.value = when {
                            !s.enabled -> "Reminders disabled. LEDs stopped."
                            !p.access -> "Grant notification listener access to start reminders."
                            !p.connected -> "Waiting for notification listener connection. LEDs stopped."
                            s.verifiedFirmware != Build.FINGERPRINT -> "Test and visually confirm the rings on this firmware first."
                            p.active && !p.bridge.authorized -> "Waiting for Shizuku. Reminders resume when access returns."
                            else -> "No monitored notifications remain. LEDs stopped."
                        }
                        return@collectLatest
                    }
                    if (!p.bridge.authorized) {
                        message.value = "Waiting for Shizuku. Reminders resume when access returns."
                        manualTest.value = null
                        return@collectLatest
                    }
                    if (!isTest && !request.foreground) return@collectLatest
                    if (!request.orientationAllowed) {
                        message.value = "Waiting for face-down orientation. LEDs stopped."
                        return@collectLatest
                    }
                    if (!isTest && !hasNotificationAccess()) {
                        refreshNotificationAccess()
                        return@collectLatest
                    }
                    var wake: PowerManager.WakeLock? = null
                    try {
                        if (s.preciseTiming || isTest) {
                            wake = requireNotNull(app.getSystemService(PowerManager::class.java))
                                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PersistentRings:active")
                                .apply { setReferenceCounted(false); acquire(120_000) }
                        }
                        running.value = true
                        message.value = if (isTest) "Sending a bounded test flash…" else "Blinking while monitored notifications remain."
                        // Renew a bounded wake lock only when an actual LED command is sent.
                        val wrapped = object : RingHardware {
                            override suspend fun write(command: LightCommand) {
                                wake?.acquire(120_000)
                                hardware.write(command)
                            }
                        }
                        val colors = if (isTest) listOf(request.test?.color ?: s.color)
                            else p.selected.apps.map { it.resolvedColor }
                        BlinkEngine(wrapped).run(s, oneShot = isTest, colors = colors)
                        if (isTest) {
                            testSent.value = true
                            message.value = "Binder accepted the test and off commands. Confirm visually whether the rings flashed."
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (e: Exception) {
                        message.value = "LED stopped: ${e.message ?: e.javaClass.simpleName}. Fix access, then tap Retry or Test."
                    } finally {
                        wake?.let { if (it.isHeld) it.release() }
                        running.value = false
                        // A settings change cancels this test before its replacement starts.
                        // Do not erase that replacement's test request during cancellation.
                        if (isTest && currentCoroutineContext().isActive) manualTest.value = null
                    }
                }
        }
    }
    private fun hasNotificationAccess(): Boolean =
        app.getSystemService(NotificationManager::class.java)
            ?.isNotificationListenerAccessGranted(ComponentName(app, RingsListener::class.java)) == true

    fun refreshNotificationAccess() { notificationAccess.value = hasNotificationAccess() }
    fun refreshBridge() { bridge.value = hardware.status() }
    fun requestPermission() {
        runCatching {
            check(Shizuku.pingBinder()) { "Start Shizuku first." }
            Shizuku.requestPermission(42)
        }.onFailure { message.value = it.message ?: "Cannot request Shizuku access." }
    }
    fun change(transform: (RingSettings) -> RingSettings) {
        scope.launch { runCatching { store.update(transform) }.onFailure { message.value = "Could not save settings: ${it.message}" } }
    }
    fun test() = startTest(ManualTest(null))
    fun testColor(rgb: Int) = startTest(ManualTest(rgb and 0xFFFFFF))

    private fun startTest(test: ManualTest) {
        if (manualTest.value != null) return
        testSent.value = false
        manualTest.value = test
    }
    fun retry() { refreshNotificationAccess(); refreshBridge(); retry.value++ }
}
