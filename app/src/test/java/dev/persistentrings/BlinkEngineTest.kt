package dev.persistentrings

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlinkEngineTest {
    @Test fun intervalIncludesPreparationAndMeasuresDarkTime() = runTest {
        val times = mutableListOf<Long>()
        val engine = BlinkEngine(object : RingHardware {
            override suspend fun write(command: LightCommand) { if (command.color != 0) times += currentTime }
        })
        val job = launch { engine.run(RingSettings(intervalSeconds=1,durationMs=500)) }
        advanceTimeBy(4_000); runCurrent()
        assertEquals(listOf(400L,1900L,3400L),times)
        job.cancelAndJoin()
    }
    @Test fun continuesBeyondOneFlashAndCancellationClears() = runTest {
        val commands = mutableListOf<Int>()
        val engine = BlinkEngine(object : RingHardware { override suspend fun write(command: LightCommand) { commands += command.color } })
        val job = launch { engine.run(RingSettings()) }
        advanceTimeBy(18_000); runCurrent()
        assertTrue(commands.count { it != 0 } >= 3)
        job.cancelAndJoin()
        assertEquals(0, commands.last())
        val count = commands.size
        advanceTimeBy(60_000); runCurrent()
        assertEquals(count,commands.size)
    }
    @Test fun liveSettingsCancelOldSessionBeforeNewSession() = runTest {
        val commands = mutableListOf<Int>()
        val hardware = object : RingHardware { override suspend fun write(command: LightCommand) { commands += command.color } }
        val state = MutableStateFlow<RingSettings?>(RingSettings(color=1))
        val job = launch { state.collectLatest { if (it != null) BlinkEngine(hardware).run(it) } }
        advanceTimeBy(600); runCurrent()
        assertEquals(1,commands.last())
        state.value = RingSettings(color=2,intervalSeconds=1)
        runCurrent()
        assertEquals(0,commands.last())
        advanceTimeBy(400); runCurrent()
        assertEquals(2,commands.last())
        state.value = null
        runCurrent()
        assertEquals(0,commands.last())
        val stopped = commands.size
        advanceTimeBy(30_000); runCurrent()
        assertEquals(stopped,commands.size)
        job.cancelAndJoin()
    }
    @Test fun oneShotDoubleFlashFinishesAndTurnsOff() = runTest {
        val commands = mutableListOf<Int>()
        BlinkEngine(object : RingHardware { override suspend fun write(command: LightCommand) { commands += command.color } })
            .run(RingSettings(pattern = LedPattern.DOUBLE_FLASH),oneShot=true)
        assertEquals(2,commands.count { it != 0 })
        assertEquals(0,commands.last())
    }
    @Test fun bridgeDeathDuringCancellationDoesNotBreakCollector() = runTest {
        var fail = false
        val hardware = object : RingHardware {
            override suspend fun write(command: LightCommand) { if (fail) error("Bridge died") }
        }
        val state = MutableStateFlow(true)
        val job = launch { state.collectLatest { if (it) BlinkEngine(hardware).run(RingSettings()) } }
        advanceTimeBy(500); runCurrent()
        fail = true
        state.value = false
        runCurrent()
        assertTrue(job.isActive)
        job.cancelAndJoin()
    }
}
