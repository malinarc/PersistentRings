package dev.persistentrings

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.*

/** Runs atomically before the first read; new string selections take precedence. */
object PatternMigration : DataMigration<Preferences> {
    val patternKey = stringPreferencesKey("pattern")
    val legacyKey = booleanPreferencesKey("double")
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val stored = currentData[patternKey]
        return currentData[legacyKey] != null ||
            (stored != null && LedPattern.fromStorage(stored).storageValue != stored)
    }
    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        this[patternKey] = LedPattern.fromStorage(this[patternKey], this[legacyKey] ?: false).storageValue
        remove(legacyKey)
    }.toPreferences()
    override suspend fun cleanUp() = Unit
}
