package dev.persistentrings

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatternCommandsTest {
    private val red = 0xFF0000
    @Test fun changingPatternClearsAndJoinsBeforeReplacement() = runTest {
        val commands = mutableListOf<LightCommand>()
        val hardware = object : RingHardware { override suspend fun write(command: LightCommand) { commands += command } }
        val settings = MutableStateFlow(RingSettings(color = red, pattern = LedPattern.SINGLE_FLASH))
        val job = launch { settings.collectLatest { BlinkEngine(hardware).run(it) } }
        advanceTimeBy(450); runCurrent()
        assertEquals(LightCommand(red), commands.last())
        settings.value = settings.value.copy(pattern = LedPattern.DOUBLE_FLASH)
        runCurrent()
        assertEquals(0,commands.last().color)
        advanceTimeBy(400); runCurrent()
        assertEquals(LightCommand(red), commands.last())
        advanceTimeBy(900); runCurrent()
        assertEquals(3, commands.count { it.color == red })
        job.cancelAndJoin()
        val size = commands.size
        advanceTimeBy(60_000); runCurrent()
        assertEquals(size,commands.size)
        assertEquals(0,commands.last().color)
    }
    @Test fun singleSequence() = runTest {
        val commands = mutableListOf<LightCommand>()
        BlinkEngine(object : RingHardware { override suspend fun write(command: LightCommand) { commands += command } })
            .run(RingSettings(color = red, durationMs = 300), true)
        assertEquals(listOf(LightCommand(0,onMs=300), LightCommand(red,onMs=300),
            LightCommand(0,onMs=300),LightCommand(0,onMs=300)),commands)
        assertEquals(700L,currentTime)
    }
    @Test fun doubleSequenceAndPause() = runTest {
        val commands = mutableListOf<Pair<Long,LightCommand>>()
        BlinkEngine(object : RingHardware { override suspend fun write(command: LightCommand) { commands += currentTime to command } })
            .run(RingSettings(color=red,durationMs=300,pattern=LedPattern.DOUBLE_FLASH),true)
        assertEquals(listOf(0L,400L,700L,1100L,1400L,1400L),commands.map { it.first })
        assertEquals(listOf(0,red,0,red,0,0),commands.map { it.second.color })
        assertTrue(commands.all { it.second.onMs == 300 })
    }
    @Test fun cancellationClearsEveryPatternAndStopsAllCommands() = runTest {
        LedPattern.entries.forEach { pattern ->
            val commands = mutableListOf<LightCommand>()
            val job = launch { BlinkEngine(object : RingHardware {
                override suspend fun write(command: LightCommand) { commands += command }
            }).run(RingSettings(color=red,pattern=pattern)) }
            advanceTimeBy(450); runCurrent()
            assertEquals(red,commands.last().color)
            job.cancelAndJoin()
            assertEquals(LightCommand(0),commands.last())
            val size = commands.size
            advanceTimeBy(60_000); runCurrent()
            assertEquals(size,commands.size)
        }
    }
}
