package dev.persistentrings

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderGateTest {
    @Test fun faceUpWaitsFaceDownRunsAndLeavingFaceDownClears() = runTest {
        val settings = MutableStateFlow(RingSettings(enabled = true, packages = setOf("dev.example.mail"), faceDownOnly = true))
        val notices = MutableStateFlow(listOf(Notice("n", "dev.example.mail", false, false)))
        val position = MutableStateFlow(OrientationState.NOT_FACE_DOWN)
        val commands = mutableListOf<LightCommand>()
        val engine = BlinkEngine(object : RingHardware {
            override suspend fun write(command: LightCommand) { commands += command }
        })
        val job = launch {
            selectedNotifications(settings, notices).combine(position) { selected, orientation ->
                selected.takeIf { it.apps.isNotEmpty() && it.settings.allowsOrientation(orientation) }
            }.distinctUntilChanged().collectLatest {
                if (it != null) engine.run(it.settings, colors = it.apps.map(ActiveReminderApp::resolvedColor))
            }
        }
        advanceTimeBy(1000)
        assertTrue(commands.isEmpty())
        position.value = OrientationState.FACE_DOWN
        runCurrent()
        advanceTimeBy(450)
        assertEquals(settings.value.color, commands.last().color)
        position.value = OrientationState.NOT_FACE_DOWN
        runCurrent()
        assertEquals(0, commands.last().color)
        val count = commands.size
        advanceTimeBy(60_000)
        assertEquals(count, commands.size)
        job.cancelAndJoin()
    }

    @Test fun appSelectionWaitsForOffCleanupAndFinalRemovalStopsAllCommands() = runTest {
        val settings = MutableStateFlow(RingSettings(enabled = true, packages = setOf("dev.example.a")))
        val notices = MutableStateFlow(listOf(
            Notice("a", "dev.example.a", false, false), Notice("b", "dev.example.b", false, false)
        ))
        val commands = mutableListOf<Pair<Long, Int>>()
        var delayCleanup = false
        val engine = BlinkEngine(object : RingHardware {
            override suspend fun write(command: LightCommand) {
                if (command.color == 0 && delayCleanup) {
                    delayCleanup = false
                    delay(200)
                }
                commands += currentTime to command.color
            }
        })
        val job = launch {
            selectedNotifications(settings, notices).map { selected ->
                selected.takeIf { it.apps.isNotEmpty() && it.settings.allowsOrientation(OrientationState.UNAVAILABLE) }
            }.distinctUntilChanged().collectLatest {
                if (it != null) engine.run(it.settings, colors = it.apps.map(ActiveReminderApp::resolvedColor))
            }
        }
        advanceTimeBy(450)
        val color = settings.value.color
        assertEquals(listOf(0L to 0, 400L to color), commands)
        delayCleanup = true
        settings.value = settings.value.copy(packages = setOf("dev.example.b"))
        runCurrent()
        advanceTimeBy(200)
        assertEquals(400L to color, commands.last())
        runCurrent()
        assertEquals(listOf(650L to 0, 650L to 0), commands.takeLast(2))
        advanceTimeBy(450)
        assertEquals(1050L to color, commands.last())
        // Package a is still posted but is no longer selected.
        notices.value = notices.value.filter { it.key != "b" }
        runCurrent()
        assertEquals(0, commands.last().second)
        val count = commands.size
        advanceTimeBy(60_000)
        assertEquals(count, commands.size)
        job.cancelAndJoin()
    }
}
