package dev.persistentrings

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleStop(intent)
        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF83D5E9))
                else lightColorScheme(primary = Color(0xFF00677D))
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { RingsScreen(vm, ::openSettings) { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) } }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleStop(intent) }
    private fun handleStop(intent: Intent?) {
        if (intent?.action == RingsListener.ACTION_STOP) vm.runtime.change { it.copy(enabled = false) }
    }
    private fun openSettings(action: String) {
        val packageUri = Uri.fromParts("package", packageName, null)
        val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        val intents = when (action) {
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS -> listOf(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(this, RingsListener::class.java).flattenToString()
                ),
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            )
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> listOf(
                Intent(action, packageUri), Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
            OPEN_HYPEROS_SETTINGS -> listOf(
                Intent("miui.intent.action.OP_AUTO_START").setPackage("com.miui.securitycenter"), appInfo
            )
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS -> listOf(appInfo)
            else -> listOf(Intent(action))
        }
        val opened = intents.any { intent ->
            intent.resolveActivity(packageManager) != null && runCatching { startActivity(intent) }.isSuccess
        }
        if (!opened) vm.runtime.message.value = "Open the requested page manually in system Settings."
    }
}

private const val OPEN_HYPEROS_SETTINGS = "openHyperOsSettings"

@Composable
private fun RingsScreen(vm: MainViewModel, open: (String) -> Unit, allowNotifications: () -> Unit) {
    val r = vm.runtime
    val s by r.settings.collectAsStateWithLifecycle()
    val bridge by r.bridge.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val connected by r.connected.collectAsStateWithLifecycle()
    val batteryExempt by vm.batteryExempt.collectAsStateWithLifecycle()
    val orientation by r.orientation.state.collectAsStateWithLifecycle()
    val count by r.count.collectAsStateWithLifecycle()
    val message by r.message.collectAsStateWithLifecycle()
    val testing by r.testing.collectAsStateWithLifecycle()
    val testSent by r.testSent.collectAsStateWithLifecycle()
    val running by r.running.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refresh() }
        owner.lifecycle.addObserver(observer)
        vm.refresh()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val verified = s.verifiedFirmware == Build.FINGERPRINT
    var showAppSelector by rememberSaveable { mutableStateOf(false) }
    if (showAppSelector) AppSelector(vm) { showAppSelector = false }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("PERSISTENT RINGS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("A reminder that stays.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Rear RGB notification lights • POCO X8 Pro", style = MaterialTheme.typography.bodyMedium)
        if (!access) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notification access required", style = MaterialTheme.typography.titleLarge)
                    Text("Allow Persistent Rings to read notification status in Android Settings before enabling reminders.")
                    Button(onClick = { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) { Text("Open notification access") }
                }
            }
        }
        Panel("Setup checklist") {
            Text("Shizuku: " + when {
                bridge.authorized -> "running and authorized"
                bridge.running -> "running, authorization needed"
                else -> "not running"
            })
            Text("Notification listener access: ${if (access) "granted" else "needed"}")
            Text("Android battery optimization: ${if (batteryExempt) "exempt" else "exemption not granted"}")
            Text("HyperOS: enable Background autostart and set Battery saver to No restrictions in App info.")
            Text("Rear-light hardware test: ${if (verified) "confirmed" else "not confirmed"}")
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow("Persistent reminders", if (verified) "Remind while selected notifications remain" else "Test and visually confirm your hardware first", s.enabled, s.enabled || (verified && access)) {
                    r.change { current -> current.copy(enabled = it) }
                }
                Text("$count active notifications", style = MaterialTheme.typography.headlineSmall)
                Text(if (running) "LED session active" else "No LED session running", style = MaterialTheme.typography.labelLarge)
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Text("Active notifications are the proxy for unread messages. Opening the shade or unlocking does not stop reminders.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Panel("Access & hardware") {
            Text("Shizuku: " + when { bridge.authorized -> "running • authorized"; bridge.running -> "permission needed"; else -> "not running" })
            OutlinedButton(onClick = r::requestPermission) { Text("Grant Shizuku access") }
            Text("Notification access: ${if (access) "granted" else "needed"} • listener ${if (connected) "connected" else "disconnected"}")
            OutlinedButton(onClick = { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) { Text("Open notification access") }
            Button(onClick = r::test, enabled = bridge.authorized && !testing) { Text(if (testing) "Testing…" else "Manual hardware test") }
            Text(if (verified) "Visually confirmed on this firmware." else "Hardware requires a visual test on this phone.", style = MaterialTheme.typography.bodySmall)
            if (testSent) {
                Text("Did both rear rings show the selected color and pattern, then go dark? A successful command is not proof of visible light.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::confirmTest) { Text("Yes, confirmed") }
                    OutlinedButton(onClick = vm::rejectTest) { Text("No") }
                }
            }
            TextButton(onClick = r::retry) { Text("Refresh access / retry reminders") }
        }
        Panel("Light settings") {
            SettingSlider("Dark interval", s.intervalSeconds.toFloat(), 1f..30f, 28, "${s.intervalSeconds} seconds") { value ->
                r.change { it.copy(intervalSeconds = value.roundToInt()) }
            }
            Text("Default 5 s. This is the dark time after one pattern cycle. Changes apply immediately.", style = MaterialTheme.typography.bodySmall)
            SettingSlider("Pulse duration", s.durationMs.toFloat(), 100f..2000f, 18, "${s.durationMs} ms") { value ->
                r.change { it.copy(durationMs = (value / 100).roundToInt() * 100) }
            }
            Text("Default LED color", fontWeight = FontWeight.Medium)
            Text("Used by apps without a custom LED color.", style = MaterialTheme.typography.bodySmall)
            var draftColor by remember(s.color) { mutableIntStateOf(s.color) }
            HsvColorWheel(
                color = draftColor,
                onPreview = { draftColor = it },
                onCommit = { selected -> r.change { it.copy(color = selected) } },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp)
                    .background(Color(0xFF000000.toInt() or draftColor), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                Column {
                    Text("Selected: #${draftColor.toString(16).padStart(6,'0').uppercase()}")
                    Text("Move to the wheel edge for maximum saturation. Pure red is #FF0000.", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = {
                draftColor = 0xFF0000
                r.change { it.copy(color = 0xFF0000) }
            }) { Text("Select pure red") }
            HorizontalDivider()
            Text("Lighting pattern", fontWeight = FontWeight.Medium)
            PatternPicker(selected = s.pattern) { selected -> r.change { it.copy(pattern = selected) } }
            Text("Both camera rings share the verified interface. Independent ring control is unavailable. Brightness follows HyperOS globally; this app does not alter that setting.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { open(Settings.ACTION_SETTINGS) }) { Text("System lighting & brightness settings") }
            Button(onClick = r::test, enabled = bridge.authorized && !testing) { Text(if (testing) "Testing…" else "Test selected color & pattern") }
        }
        Panel("Monitored applications") {
            Text("${s.packages.size} apps selected")
            Button(onClick = { showAppSelector = true }) { Text("Choose installed apps") }
            Text("Group summaries and ongoing notifications (such as calls or connection status) are excluded.", style = MaterialTheme.typography.bodySmall)
        }
        Panel("Phone orientation") {
            ToggleRow(
                "Only remind when the phone is face down",
                "LED reminders run only when the screen faces the surface and the rear camera rings face upward.",
                s.faceDownOnly,
                r.orientation.available || s.faceDownOnly
            ) { enabled -> r.change { it.copy(faceDownOnly = enabled && r.orientation.available) } }
            if (orientation == OrientationState.UNAVAILABLE) {
                Text("Orientation is unavailable. A working gravity sensor or accelerometer is required.")
            } else if (!r.orientation.hasWakeUpSensor) {
                Text("This device has no wake-up gravity sensor or accelerometer. Face-down detection may be delayed while the CPU sleeps.")
            }
            Text("The manual hardware test works in any orientation.", style = MaterialTheme.typography.bodySmall)
        }
        Panel("Background operation") {
            ToggleRow("Precise screen-off timing", "Holds a partial wake lock only during reminders. Uses more battery. Without it, flashes may be delayed while asleep.", s.preciseTiming) { value -> r.change { it.copy(preciseTiming = value) } }
            Text("1. Start Shizuku using wireless debugging, then grant this app access. Non-root Shizuku must be restarted after every phone reboot.\n\n2. Enable HyperOS rear lighting in Settings → Additional settings → Back light effects. Check its schedule. Disable competing notification effects manually if they interrupt this app.\n\n3. Enable notification access above. If Android blocks this for a sideloaded app, open App info → ⋮ → Allow restricted settings, then try again.\n\n4. In this app’s HyperOS App info, enable Background autostart and choose Battery saver → No restrictions. Lock the app in Recents if your firmware offers it.\n\n5. Allow this app’s status notification so you can see reminders are running and disable them.")
            OutlinedButton(onClick = { open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }, enabled = !batteryExempt) {
                Text("Request unrestricted battery operation")
            }
            Text("Approve the Android system dialog to grant the exemption. Set HyperOS battery and autostart options separately.")
            OutlinedButton(onClick = { open(OPEN_HYPEROS_SETTINGS) }) { Text("Open HyperOS autostart settings") }
            OutlinedButton(onClick = { open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) }) { Text("Open app info / battery settings") }
            OutlinedButton(onClick = { open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }) { Text("Open battery optimization") }
            OutlinedButton(onClick = allowNotifications) { Text("Allow status notifications") }
            Text("The listener restores current notifications after reconnecting; it does not replay a saved unread count. HyperOS may still stop the process, Doze may delay work, and other lighting scenes can override it. Force-stop prevents recovery until you reopen the app.", style = MaterialTheme.typography.bodySmall)
        }
        Text("On-device only. No message content is stored or uploaded. Firmware: ${Build.DISPLAY}\nModel: ${Build.MODEL} • Android ${Build.VERSION.RELEASE}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun HsvColorWheel(color: Int, onPreview: (Int) -> Unit, onCommit: (Int) -> Unit) {
    val hsv = remember(color) { HsvColor.fromRgb(color) }
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val outline = MaterialTheme.colorScheme.outline
    // Identical inset and radius for rendering AND touch mapping. V is always 1.
    Canvas(Modifier.fillMaxWidth().height(240.dp).pointerInput(Unit) {
        val inset = 12.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f - inset
            if (hypot(down.position.x - center.x, down.position.y - center.y) <= radius) {
                fun colorAt(position: Offset) = HsvColor.at(
                    position.x - center.x, position.y - center.y, radius).toRgb()
                val pointerId = down.id
                var latest = colorAt(down.position)
                preview(latest)
                down.consume()
                var finished = false
                while (!finished) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (change.isConsumed) break
                    latest = colorAt(change.position)
                    preview(latest)
                    change.consume()
                    finished = !change.pressed
                }
                // No writes during movement, nor if another handler or disposal cancels us.
                if (finished) commit(latest)
            }
        }
    }) {
        val radius = min(size.width, size.height) / 2f - 12.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green,
            Color.Cyan, Color.Blue, Color.Magenta, Color.Red), center), radius, center)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius), radius, center)
        drawCircle(outline, radius, center, style = Stroke(1.dp.toPx()))
        val angle = Math.toRadians(hsv.hue.toDouble())
        val pointer = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (hsv.saturation * radius)
        drawCircle(Color.Black, 10.dp.toPx(), pointer, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, 7.dp.toPx(), pointer, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun PatternPicker(selected: LedPattern, select: (LedPattern) -> Unit) {
    LedPattern.entries.forEach { pattern ->
        Row(
            Modifier.fillMaxWidth().clickable { select(pattern) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = pattern == selected, onClick = { select(pattern) })
            Column(Modifier.padding(start = 6.dp)) {
                Text(pattern.title, fontWeight = FontWeight.Medium)
                Text(pattern.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
@Composable private fun ToggleRow(title: String, description: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = change, enabled = enabled)
    }
}
@Composable private fun SettingSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, label: String, save: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Text("$title • $label", fontWeight = FontWeight.Medium)
    Slider(value = draft, onValueChange = { draft = it }, onValueChangeFinished = { save(draft) }, valueRange = range, steps = steps)
}
