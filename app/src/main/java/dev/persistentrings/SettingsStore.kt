package dev.persistentrings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore("rings", produceMigrations = { listOf(PatternMigration) })
class SettingsStore internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.settingsDataStore)
    private val enabled = booleanPreferencesKey("enabled")
    private val interval = intPreferencesKey("interval")
    private val duration = intPreferencesKey("duration")
    private val color = intPreferencesKey("color")
    private val double = booleanPreferencesKey("double")
    private val pattern = stringPreferencesKey("pattern")
    private val precise = booleanPreferencesKey("precise")
    private val packages = stringSetPreferencesKey("packages")
    private val verified = stringPreferencesKey("verified_firmware")
    private val faceDownOnly = booleanPreferencesKey("face_down_only")
    // Versioned entries use a package identifier followed by '=' and six RGB hex digits.
    private val appColors = stringSetPreferencesKey("app_colors_v1")
    val settings: Flow<RingSettings> = store.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }.map(::decode)

    private fun decode(p: Preferences) = RingSettings(
        enabled = p[enabled] ?: false,
        intervalSeconds = (p[interval] ?: 5).coerceIn(1,30),
        durationMs = (p[duration] ?: 500).coerceIn(100,2000),
        color = (p[color] ?: 0xFF0000) and 0xFFFFFF,
        pattern = LedPattern.fromStorage(p[pattern], p[double] ?: false),
        preciseTiming = p[precise] ?: true,
        packages = p[packages] ?: emptySet(),
        verifiedFirmware = p[verified] ?: "",
        faceDownOnly = p[faceDownOnly] ?: false,
        appColors = p[appColors].orEmpty().mapNotNull { entry ->
            val packageName = entry.substringBefore('=')
            val hex = entry.substringAfter('=', "")
            val rgb = hex.takeIf { it.length == 6 && it.all { digit -> digit.isDigit() || digit.lowercaseChar() in 'a'..'f' } }
                ?.toIntOrNull(16)
            if (packageName.isNotEmpty() && rgb != null) packageName to rgb else null
        }.toMap()
    )
    suspend fun update(transform: (RingSettings) -> RingSettings) {
        // All writers execute on the main dispatcher; DataStore itself serializes the edit.
        store.edit { p ->
            val n = transform(decode(p))
            p[enabled]=n.enabled; p[interval]=n.intervalSeconds.coerceIn(1,30)
            p[duration]=n.durationMs.coerceIn(100,2000); p[color]=n.color and 0xFFFFFF
            p[pattern]=n.pattern.storageValue; p.remove(double); p[precise]=n.preciseTiming
            p[packages] = n.packages.toSet()
            p[verified] = n.verifiedFirmware
            p[faceDownOnly] = n.faceDownOnly
            p[appColors] = n.appColors.mapTo(mutableSetOf()) { (packageName, rgb) ->
                "$packageName=${(rgb and 0xFFFFFF).toString(16).padStart(6, '0')}"
            }
        }
    }
}
