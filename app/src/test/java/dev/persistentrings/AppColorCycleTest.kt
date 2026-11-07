package dev.persistentrings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppColorCycleTest {
    private val blue = 0x0000FF
    private val green = 0x00FF00
    private val red = 0xFF0000
    private val settings = RingSettings(
        packages = setOf("app.a", "app.b", "app.c"),
        color = green,
        appColors = mapOf("app.a" to blue),
        durationMs = 300,
        intervalSeconds = 2
    )

    private fun notice(key: String, pkg: String, time: Long) =
        Notice(key, pkg, summary = false, ongoing = false, postTime = time)

    @Test fun groupsByPackageAndSortsNewestThenByPackageName() = runTest {
        val snapshot = listOf(
            notice("c", "app.c", 20),
            notice("a-old", "app.a", 1),
            notice("a-new", "app.a", 30),
            notice("b", "app.b", 20),
            notice("other", "app.unselected", 100),
            notice("summary", "app.c", 90).copy(summary = true)
        )
        val selected = selectedNotifications(MutableStateFlow(settings), MutableStateFlow(snapshot)).first()
        assertEquals(4, selected.count)
        assertEquals(listOf(
            ActiveReminderApp("app.a", 30, blue),
            ActiveReminderApp("app.b", 20, green),
            ActiveReminderApp("app.c", 20, green)
        ), selected.apps)
    }

    @Test fun twoAppsClearBetweenSlotsAndApplyDarkIntervalAfterFinalSlot() = runTest {
        val selected = selectedNotifications(
            MutableStateFlow(settings),
            MutableStateFlow(listOf(notice("b", "app.b", 1), notice("a", "app.a", 2)))
        ).first()
        val commands = mutableListOf<Pair<Long, Int>>()
        val engine = BlinkEngine(object : RingHardware {
            override suspend fun write(command: LightCommand) { commands += currentTime to command.color }
        })
        val job = launch {
            engine.run(selected.settings, colors = selected.apps.map(ActiveReminderApp::resolvedColor))
        }
        advanceTimeBy(3400)
        runCurrent()
        assertEquals(listOf(
            0L to 0, 400L to blue, 700L to 0, 1100L to green, 1400L to 0,
            3000L to 0, 3400L to blue
        ), commands)
        job.cancelAndJoin()
        assertEquals(0, commands.last().second)
    }

    @Test fun equalResolvedColorsStillProduceTwoSlots() = runTest {
        val selected = selectedNotifications(
            MutableStateFlow(settings),
            MutableStateFlow(listOf(notice("c", "app.c", 20), notice("b", "app.b", 20)))
        ).first()
        assertEquals(listOf("app.b", "app.c"), selected.apps.map { it.packageName })
        val commands = mutableListOf<Int>()
        val engine = BlinkEngine(object : RingHardware {
            override suspend fun write(command: LightCommand) { commands += command.color }
        })
        engine.run(settings, oneShot = true, colors = selected.apps.map(ActiveReminderApp::resolvedColor))
        assertEquals(listOf(0, green, 0, green, 0, 0), commands)
    }

    @Test fun removalWaitsForOffCleanupAndFinalRemovalStopsTheCycle() = runTest {
        val notices = MutableStateFlow(listOf(notice("a", "app.a", 2), notice("b", "app.b", 1)))
        val commands = mutableListOf<Pair<Long, Int>>()
        var deferOff = false
        val engine = BlinkEngine(object : RingHardware {
            override suspend fun write(command: LightCommand) {
                if (command.color == 0 && deferOff) {
                    deferOff = false
                    delay(150)
                }
                commands += currentTime to command.color
            }
        })
        val job = launch {
            selectedNotifications(MutableStateFlow(settings), notices).distinctUntilChanged().collectLatest { selected ->
                if (selected.apps.isNotEmpty()) {
                    engine.run(selected.settings, colors = selected.apps.map(ActiveReminderApp::resolvedColor))
                }
            }
        }
        advanceTimeBy(450)
        assertEquals(400L to blue, commands.last())
        deferOff = true
        notices.value = listOf(notice("b", "app.b", 1))
        runCurrent()
        advanceTimeBy(150)
        assertEquals(400L to blue, commands.last())
        runCurrent()
        assertEquals(listOf(600L to 0, 600L to 0), commands.takeLast(2))
        advanceTimeBy(450)
        assertEquals(1000L to green, commands.last())
        notices.value = emptyList()
        runCurrent()
        assertEquals(1050L to 0, commands.last())
        val count = commands.size
        advanceTimeBy(60_000)
        assertEquals(count, commands.size)
        job.cancelAndJoin()
    }

    @Test fun changesToColorsAndNewNotificationsReplaceTheRunningCycle() = runTest {
        val current = MutableStateFlow(settings)
        val notices = MutableStateFlow(listOf(notice("a", "app.a", 2), notice("b", "app.b", 1)))
        val commands = mutableListOf<Int>()
        val engine = BlinkEngine(object : RingHardware {
            override suspend fun write(command: LightCommand) { commands += command.color }
        })
        val job = launch {
            selectedNotifications(current, notices).distinctUntilChanged().collectLatest { selected ->
                engine.run(selected.settings, colors = selected.apps.map(ActiveReminderApp::resolvedColor))
            }
        }
        advanceTimeBy(450)
        assertEquals(blue, commands.last())
        current.value = settings.copy(appColors = mapOf("app.a" to red))
        runCurrent()
        assertEquals(0, commands.last())
        advanceTimeBy(450)
        assertEquals(red, commands.last())

        val white = 0xFFFFFF
        current.value = current.value.copy(color = white)
        runCurrent()
        assertEquals(0, commands.last())
        advanceTimeBy(1150)
        assertEquals(listOf(red, white), commands.filter { it != 0 }.takeLast(2))

        notices.value = notices.value + notice("b-new", "app.b", 3)
        runCurrent()
        assertEquals(0, commands.last())
        advanceTimeBy(450)
        assertEquals(white, commands.last())
        job.cancelAndJoin()
        assertEquals(0, commands.last())
    }
}
