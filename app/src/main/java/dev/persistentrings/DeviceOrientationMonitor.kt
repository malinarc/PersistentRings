package dev.persistentrings

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import kotlin.math.sqrt

enum class OrientationState { FACE_DOWN, NOT_FACE_DOWN, UNAVAILABLE }

/** Sensor timestamps are monotonic and use the same time base across samples. */
internal class FaceDownDetector {
    private var state = OrientationState.NOT_FACE_DOWN
    private var candidateSinceMs: Long? = null

    fun update(normalizedZ: Double, timestampMs: Long): OrientationState {
        val crossesThreshold = if (state == OrientationState.FACE_DOWN) {
            normalizedZ >= -0.65
        } else {
            normalizedZ <= -0.82
        }
        if (!normalizedZ.isFinite() || !crossesThreshold) {
            candidateSinceMs = null
            return state
        }
        val since = candidateSinceMs
        if (since == null || timestampMs < since) {
            candidateSinceMs = timestampMs
        } else if (timestampMs - since >= 500) {
            state = if (state == OrientationState.FACE_DOWN) OrientationState.NOT_FACE_DOWN else OrientationState.FACE_DOWN
            candidateSinceMs = null
        }
        return state
    }
}

internal data class OrientationSample(
    val x: Double, val y: Double, val z: Double, val timestampMs: Long,
    val fromAccelerometer: Boolean
) {
    fun normalizedZ(): Double {
        val magnitude = sqrt(x * x + y * y + z * z)
        return if (magnitude > 0.1) z / magnitude else Double.NaN
    }

    fun filteredAfter(previous: OrientationSample?): OrientationSample {
        if (!fromAccelerometer || previous == null) return this
        val elapsed = (timestampMs - previous.timestampMs).coerceAtLeast(0).toDouble()
        val weight = elapsed / (elapsed + 200.0)
        return copy(
            x = previous.x + weight * (x - previous.x),
            y = previous.y + weight * (y - previous.y),
            z = previous.z + weight * (z - previous.z)
        )
    }
}

internal interface OrientationSource {
    val available: Boolean
    val hasWakeUpSensor: Boolean
    fun register(sample: (OrientationSample) -> Unit): AutoCloseable?
}

internal class DeviceOrientationMonitor(private val source: OrientationSource) {
    val available get() = source.available
    val hasWakeUpSensor get() = source.hasWakeUpSensor
    private val mutableState = MutableStateFlow(idleState())
    val state: StateFlow<OrientationState> = mutableState.asStateFlow()

    suspend fun track(needed: Flow<Boolean>) {
        needed.distinctUntilChanged().collectLatest { shouldRegister ->
            if (!shouldRegister || !available) return@collectLatest
            val detector = FaceDownDetector()
            var previous: OrientationSample? = null
            val registration = source.register { sample ->
                val filtered = sample.filteredAfter(previous)
                previous = filtered
                mutableState.value = detector.update(filtered.normalizedZ(), sample.timestampMs)
            }
            if (registration == null) {
                mutableState.value = OrientationState.UNAVAILABLE
                return@collectLatest
            }
            try {
                awaitCancellation()
            } finally {
                registration.close()
                mutableState.value = idleState()
            }
        }
    }

    private fun idleState() = if (available) OrientationState.NOT_FACE_DOWN else OrientationState.UNAVAILABLE
}
