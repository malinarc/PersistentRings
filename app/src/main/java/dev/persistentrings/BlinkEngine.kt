package dev.persistentrings

import kotlinx.coroutines.*

data class LightCommand(
    val color: Int,
    val onMs: Int = 500
)

interface RingHardware { suspend fun write(command: LightCommand) }
/** The caller owns exactly one instance of this session, using collectLatest (cancel + join). */
class BlinkEngine(private val hardware: RingHardware) {
    suspend fun run(settings: RingSettings, oneShot: Boolean = false, colors: List<Int> = listOf(settings.color)) {
        require(colors.isNotEmpty())
        try {
            do {
                // Research on X8 Pro finds a clear + >=350ms settle necessary for reliability.
                hardware.clear(settings.durationMs)
                delay(400)
                colors.forEachIndexed { index, color ->
                    if (index > 0) delay(400)
                    play(settings, color)
                }
                // Slider is dark time AFTER the last flash, rather than start-to-start time.
                // The next iteration's 400ms clear/settle is part of the selected dark time.
                if (!oneShot) delay(settings.intervalSeconds * 1000L - 400)
            } while (!oneShot && currentCoroutineContext().isActive)
        } finally {
            val cancelled = !currentCoroutineContext().isActive
            withContext(NonCancellable) {
                try { hardware.clear(settings.durationMs) }
                catch (e: Exception) {
                    // A dead bridge during cancellation must not kill the state collector.
                    // The pulse's finite duration is a fallback if the bridge dies.
                    if (!cancelled) throw e
                }
            }
        }
    }

    private suspend fun play(settings: RingSettings, color: Int) {
        when (settings.pattern) {
            LedPattern.SINGLE_FLASH -> pulse(color, settings.durationMs)
            LedPattern.DOUBLE_FLASH -> {
                pulse(color, settings.durationMs)
                delay(400)
                pulse(color, settings.durationMs)
            }
        }
    }

    private suspend fun pulse(color: Int, durationMs: Int) {
        hardware.write(LightCommand(color = color, onMs = durationMs))
        delay(durationMs.toLong())
        hardware.clear(durationMs)
    }

    private suspend fun RingHardware.clear(durationMs: Int) {
        write(LightCommand(color = 0, onMs = durationMs.coerceAtLeast(100)))
    }
}
