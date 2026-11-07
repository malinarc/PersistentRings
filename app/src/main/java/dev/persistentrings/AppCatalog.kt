package dev.persistentrings

import java.text.Collator
import java.util.Locale

internal data class InstalledApp(
    val packageName: String,
    val label: String = packageName,
    val enabled: Boolean = true,
    val system: Boolean = false,
    val launchable: Boolean = false
)

internal fun appCatalog(
    installed: List<InstalledApp>,
    observedPackages: Set<String>,
    ownPackage: String,
    locale: Locale = Locale.getDefault()
): List<InstalledApp> {
    val byPackage = installed.associateBy { it.packageName }
    val observed = observedPackages.filterNot { it in byPackage }.map { InstalledApp(it) }
    val collator = Collator.getInstance(locale)
    return (byPackage.values + observed)
        .filter { it.enabled && it.packageName != ownPackage }
        .map { if (it.label.isBlank()) it.copy(label = it.packageName) else it }
        .sortedWith { left, right ->
            collator.compare(left.label, right.label).takeIf { it != 0 }
                ?: left.packageName.compareTo(right.packageName)
        }
}

internal fun List<InstalledApp>.visibleApps(
    query: String, showSystem: Boolean, observed: Set<String>, selected: Set<String>
) = filter { app ->
    val visible = showSystem || !app.system || app.launchable ||
        app.packageName in observed || app.packageName in selected
    val matches = app.label.contains(query.trim(), ignoreCase = true) ||
        app.packageName.contains(query.trim(), ignoreCase = true)
    visible && matches
}

internal fun RingSettings.selectApps(apps: List<InstalledApp>) =
    copy(packages = packages + apps.map { it.packageName })

internal fun RingSettings.clearApps() = copy(packages = emptySet())
