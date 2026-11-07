package dev.persistentrings

import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import okio.Path.Companion.toPath
import okio.FileSystem
import androidx.datastore.core.okio.OkioStorage

class PatternMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun legacyTrueAndFalseMigrateToStableStrings() = runTest {
        listOf(true to LedPattern.DOUBLE_FLASH, false to LedPattern.SINGLE_FLASH).forEach { (old, expected) ->
            val input = mutablePreferencesOf(PatternMigration.legacyKey to old)
            assertTrue(PatternMigration.shouldMigrate(input))
            val result = PatternMigration.migrate(input)
            assertEquals(expected.storageValue, result[PatternMigration.patternKey])
            assertNull(result[PatternMigration.legacyKey])
            assertFalse(PatternMigration.shouldMigrate(result))
        }
    }
    @Test fun newSelectionTakesPrecedenceAndUnknownValueFallsBack() = runTest {
        val input = mutablePreferencesOf(PatternMigration.legacyKey to true,
            PatternMigration.patternKey to LedPattern.SINGLE_FLASH.storageValue)
        assertEquals("single_flash", PatternMigration.migrate(input)[PatternMigration.patternKey])
        assertEquals(LedPattern.SINGLE_FLASH, LedPattern.fromStorage("future_pattern"))
        LedPattern.entries.forEach { assertEquals(it, LedPattern.fromStorage(it.storageValue)) }
    }
    @Test fun retiredAndInvalidPatternsAreRewrittenToSingleFlash() = runTest {
        for (stored in listOf("slow_breath", "fast_breath", "short_steady", "future_pattern", "")) {
            val input = preferencesOf(PatternMigration.patternKey to stored)
            assertEquals(LedPattern.SINGLE_FLASH, LedPattern.fromStorage(stored, legacyDoubleFlash = true))
            assertTrue(PatternMigration.shouldMigrate(input))
            val migrated = PatternMigration.migrate(input)
            assertEquals("single_flash", migrated[PatternMigration.patternKey])
            assertFalse(PatternMigration.shouldMigrate(migrated))
        }
        assertEquals(listOf("Single flash", "Double flash"), LedPattern.entries.map { it.title })
        for (valid in LedPattern.entries) {
            assertFalse(PatternMigration.shouldMigrate(preferencesOf(PatternMigration.patternKey to valid.storageValue)))
        }
    }
    @Test fun migrationRunsBeforeFirstReadAndSurvivesReopen() = runTest {
        val file = java.io.File(folder.root, "migration.preferences_pb")
        // Okio's atomic replacement supports Windows; Android's FileStorage backend
        // relies on Linux rename semantics and cannot replace this file on Windows.
        fun storage() = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer,
            producePath = { file.absolutePath.toPath() })
        fun store(scope: CoroutineScope) = PreferenceDataStoreFactory.create(
            storage = storage(), migrations = listOf(PatternMigration), scope = scope)
        val oldScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val oldStore = PreferenceDataStoreFactory.create(storage = storage(), scope = oldScope)
        oldStore.edit { it[PatternMigration.legacyKey] = true }
        oldScope.coroutineContext[Job]!!.cancelAndJoin()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val migrated = store(scope).data.first()
        assertEquals("double_flash", migrated[PatternMigration.patternKey])
        assertNull(migrated[PatternMigration.legacyKey])
        scope.coroutineContext[Job]!!.cancelAndJoin()
        val reopenScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        assertEquals("double_flash", store(reopenScope).data.first()[PatternMigration.patternKey])
        reopenScope.coroutineContext[Job]!!.cancelAndJoin()
    }
}
