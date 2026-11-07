package dev.persistentrings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun AppSelector(vm: MainViewModel, dismiss: () -> Unit) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val observed by vm.observedPackages.collectAsStateWithLifecycle()
    val settings by vm.runtime.settings.collectAsStateWithLifecycle()
    val loading by vm.appsLoading.collectAsStateWithLifecycle()
    val loadFailed by vm.appsError.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var editingPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = remember(apps, query, showSystem, observed, settings.packages) {
        apps.visibleApps(query, showSystem, observed, settings.packages)
    }
    LifecycleResumeEffect(vm) {
        vm.refreshApps()
        onPauseOrDispose {}
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 12.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Select applications", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = dismiss) { Text("Done") }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Search apps or packages") }, modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Show system apps", Modifier.weight(1f))
                    Switch(checked = showSystem, onCheckedChange = { showSystem = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.selectApps(visible) }, enabled = visible.isNotEmpty()) {
                        Text("Select all")
                    }
                    TextButton(onClick = vm::clearApps, enabled = settings.packages.isNotEmpty()) { Text("Clear all") }
                }
                Text("${settings.packages.size} selected. Select all adds the apps shown. Clear all removes every selection.",
                    style = MaterialTheme.typography.bodySmall)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (loadFailed) {
                    Text("Installed apps could not be loaded. Apps seen in notifications may still appear.")
                    TextButton(onClick = vm::refreshApps) { Text("Reload apps") }
                }
                if (visible.isEmpty() && !loading) Text("No matching applications.")
                LazyColumn(Modifier.weight(1f)) {
                    items(visible, key = { it.packageName }) { app ->
                        val icon by produceState<ImageBitmap?>(null, app.packageName) {
                            value = vm.appIcon(app.packageName)?.asImageBitmap()
                        }
                        val selected = app.packageName in settings.packages
                        val color = settings.colorFor(app.packageName)
                        val custom = app.packageName in settings.appColors
                        ListItem(
                            headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Column {
                                    Text(app.packageName)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ColorSwatch(color)
                                        Text("${if (custom) "Custom" else "Default"} ${rgbHex(color)}")
                                    }
                                    TextButton(onClick = { editingPackage = app.packageName }) { Text("Set app color") }
                                }
                            },
                            leadingContent = {
                                if (icon != null) {
                                    Image(requireNotNull(icon), contentDescription = null, Modifier.size(36.dp))
                                } else {
                                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                        Text(app.label.take(1).uppercase())
                                    }
                                }
                            },
                            trailingContent = {
                                Switch(checked = selected, onCheckedChange = { enabled ->
                                    vm.runtime.change { current ->
                                        current.copy(packages = if (enabled) current.packages + app.packageName
                                            else current.packages - app.packageName)
                                    }
                                })
                            }
                        )
                    }
                }
            }
        }
    }
    editingPackage?.let { pkg ->
        key(pkg) {
            AppColorEditor(
                title = apps.firstOrNull { it.packageName == pkg }?.label ?: pkg,
                initialColor = settings.colorFor(pkg),
                hasOverride = pkg in settings.appColors,
                runtime = vm.runtime,
                dismiss = { editingPackage = null },
                save = { color ->
                    vm.runtime.change { it.copy(appColors = it.appColors + (pkg to color)) }
                    editingPackage = null
                },
                useDefault = {
                    vm.runtime.change { it.copy(appColors = it.appColors - pkg) }
                    editingPackage = null
                }
            )
        }
    }
}

@Composable
private fun AppColorEditor(
    title: String,
    initialColor: Int,
    hasOverride: Boolean,
    runtime: RingsRuntime,
    dismiss: () -> Unit,
    save: (Int) -> Unit,
    useDefault: () -> Unit
) {
    var draft by rememberSaveable { mutableIntStateOf(initialColor) }
    var tested by rememberSaveable { mutableStateOf(false) }
    val bridge by runtime.bridge.collectAsStateWithLifecycle()
    val testing by runtime.testing.collectAsStateWithLifecycle()
    val testSent by runtime.testSent.collectAsStateWithLifecycle()
    val message by runtime.message.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("App color for $title") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HsvColorWheel(draft, onPreview = { draft = it }, onCommit = { draft = it })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ColorSwatch(draft)
                    Text(rgbHex(draft))
                }
                TextButton(onClick = { draft = 0xFF0000 }) { Text("Select pure red") }
                OutlinedButton(
                    onClick = { tested = true; runtime.testColor(draft) },
                    enabled = bridge.authorized && !testing
                ) { Text(if (testing) "Testing…" else "Test this app color") }
                if (tested) {
                    Text(if (testSent) "Test sent. Check the rear rings." else message)
                }
                TextButton(onClick = useDefault, enabled = hasOverride) { Text("Use default color") }
            }
        },
        confirmButton = { Button(onClick = { save(draft) }) { Text("Save app color") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ColorSwatch(rgb: Int) {
    Box(
        Modifier.size(16.dp)
            .background(Color(0xFF000000.toInt() or rgb), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    )
}

private fun rgbHex(rgb: Int) = "#" + rgb.toString(16).padStart(6, '0').uppercase()
