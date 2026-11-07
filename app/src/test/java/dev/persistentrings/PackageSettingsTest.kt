package dev.persistentrings

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PackageSettingsTest {
    @get:Rule val folder = TemporaryFolder()
    private val packagesKey = stringSetPreferencesKey("packages")

    private suspend fun withStore(block: suspend (androidx.datastore.core.DataStore<Preferences>) -> Unit) {
        val file = java.io.File(folder.root, "settings.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer, producePath = { file.absolutePath.toPath() })
        try {
            block(PreferenceDataStoreFactory.create(storage = storage, migrations = listOf(PatternMigration), scope = scope))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

    @Test fun freshInstallIsEmptyAndArbitraryPackagesSurviveReopen() = runTest {
        val selection = setOf("dev.example.calendar", "dev.example.headless")
        withStore { data ->
            val settings = SettingsStore(data)
            assertTrue(settings.settings.first().packages.isEmpty())
            assertFalse(settings.settings.first().faceDownOnly)
            settings.update { it.copy(packages = selection, faceDownOnly = true) }
        }
        withStore { data ->
            val settings = SettingsStore(data).settings.first()
            assertEquals(selection, settings.packages)
            assertTrue(settings.faceDownOnly)
        }
    }

    @Test fun upgradePreservesStoredSetIncludingExplicitEmptySelection() = runTest {
        val old = setOf("com.whatsapp", "org.telegram.messenger")
        withStore { it.edit { prefs ->
            prefs[packagesKey] = old
            prefs[intPreferencesKey("color")] = 0x56ABCD
            prefs[PatternMigration.patternKey] = "slow_breath"
        } }
        withStore { data ->
            val settings = SettingsStore(data)
            assertEquals(old, settings.settings.first().packages)
            assertEquals(0x56ABCD, settings.settings.first().color)
            assertEquals("single_flash", data.data.first()[PatternMigration.patternKey])
            settings.update { it.copy(faceDownOnly = true) }
            assertEquals(old, settings.settings.first().packages)
            settings.update { it.clearApps() }
        }
        withStore { data -> assertTrue(SettingsStore(data).settings.first().packages.isEmpty()) }
    }

    @Test fun arbitraryColorOverridesSurviveDeselectAndReopen() = runTest {
        val calendar = "dev.example.calendar"
        val mail = "org.custom.mail"
        withStore { data ->
            SettingsStore(data).update {
                it.copy(packages = setOf(calendar, mail), color = 0x0000FF,
                    appColors = mapOf(calendar to 0xFF0000, mail to 0xFF00FF00.toInt()))
            }
            assertEquals(setOf("$calendar=ff0000", "$mail=00ff00"),
                data.data.first()[stringSetPreferencesKey("app_colors_v1")])
            SettingsStore(data).update { it.copy(packages = it.packages - calendar) }
        }
        withStore { data ->
            val store = SettingsStore(data)
            val settings = store.settings.first()
            assertEquals(setOf(mail), settings.packages)
            assertEquals(0xFF0000, settings.colorFor(calendar))
            assertEquals(0x00FF00, settings.colorFor(mail))
            assertEquals(0x0000FF, settings.colorFor("other.app"))
            store.update { it.copy(packages = it.packages + calendar) }
            assertEquals(0xFF0000, store.settings.first().colorFor(calendar))
            store.update { it.copy(appColors = it.appColors - calendar) }
        }
        withStore { data ->
            val settings = SettingsStore(data).settings.first()
            assertEquals(setOf(calendar, mail), settings.packages)
            assertEquals(0x0000FF, settings.colorFor(calendar))
            assertEquals(mapOf(mail to 0x00FF00), settings.appColors)
        }
    }
}
