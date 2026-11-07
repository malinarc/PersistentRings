package dev.persistentrings

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceOrientationTest {
    @Test fun entryAndExitRequireExactThresholdsAnd500Milliseconds() {
        val detector = FaceDownDetector()
        assertEquals(OrientationState.NOT_FACE_DOWN, detector.update(-0.81, 0))
        assertEquals(OrientationState.NOT_FACE_DOWN, detector.update(-0.82, 100))
        assertEquals(OrientationState.NOT_FACE_DOWN, detector.update(-0.82, 599))
        assertEquals(OrientationState.FACE_DOWN, detector.update(-0.82, 600))
        assertEquals(OrientationState.FACE_DOWN, detector.update(-0.66, 1300))
        assertEquals(OrientationState.FACE_DOWN, detector.update(-0.65, 1500))
        assertEquals(OrientationState.FACE_DOWN, detector.update(-0.65, 1999))
        assertEquals(OrientationState.NOT_FACE_DOWN, detector.update(-0.65, 2000))
    }

    @Test fun crossingBackIntoHysteresisResetsPendingTransition() {
        val detector = FaceDownDetector()
        detector.update(-1.0, 0)
        detector.update(-0.70, 400)
        assertEquals(OrientationState.NOT_FACE_DOWN, detector.update(-1.0, 500))
        assertEquals(OrientationState.FACE_DOWN, detector.update(-1.0, 1000))
        detector.update(0.0, 1200)
        detector.update(-0.70, 1400)
        assertEquals(OrientationState.FACE_DOWN, detector.update(0.0, 1700))
        assertEquals(OrientationState.NOT_FACE_DOWN, detector.update(0.0, 2200))
    }

    @Test fun normalizesGravityAndFiltersAccelerometerChanges() {
        val down = OrientationSample(0.0, 0.0, -9.8, 0, true)
        val up = OrientationSample(0.0, 0.0, 9.8, 100, true)
        assertEquals(-1.0, down.normalizedZ(), 0.00001)
        assertEquals(1.0, up.normalizedZ(), 0.00001)
        assertTrue(up.filteredAfter(down).z < 0)
        assertEquals(up.z, up.copy(fromAccelerometer = false).filteredAfter(down).z, 0.00001)
    }

    @Test fun unavailableSensorDoesNotRegister() = runTest {
        val source = FakeSource(available = false)
        val monitor = DeviceOrientationMonitor(source)
        val job = launch { monitor.track(MutableStateFlow(true)) }
        runCurrent()
        assertEquals(OrientationState.UNAVAILABLE, monitor.state.value)
        assertEquals(0, source.starts)
        job.cancelAndJoin()
    }

    @Test fun registersOnlyForEligibleFaceDownOperationAndReleasesOnIdle() = runTest {
        val source = FakeSource()
        val monitor = DeviceOrientationMonitor(source)
        val settings = MutableStateFlow(RingSettings(packages = setOf("arbitrary.app"), faceDownOnly = true))
        val notices = MutableStateFlow<List<Notice>>(emptyList())
        val job = launch {
            monitor.track(selectedNotifications(settings, notices).map { it.needsOrientation(foreground = true) })
        }
        runCurrent()
        assertEquals(0, source.starts)
        notices.value = listOf(Notice("n", "arbitrary.app", false, false))
        runCurrent()
        assertEquals(1, source.starts)
        source.emit(-9.8, 0)
        source.emit(-9.8, 500)
        assertEquals(OrientationState.FACE_DOWN, monitor.state.value)
        notices.value = emptyList()
        runCurrent()
        assertEquals(1, source.stops)
        assertEquals(OrientationState.NOT_FACE_DOWN, monitor.state.value)
        assertNull(source.callback)
        job.cancelAndJoin()
    }

    private class FakeSource(override val available: Boolean = true) : OrientationSource {
        override val hasWakeUpSensor = false
        var starts = 0
        var stops = 0
        var callback: ((OrientationSample) -> Unit)? = null
        override fun register(sample: (OrientationSample) -> Unit): AutoCloseable {
            starts++
            callback = sample
            return AutoCloseable { stops++; callback = null }
        }
        fun emit(z: Double, time: Long) {
            callback?.invoke(OrientationSample(0.0, 0.0, z, time, false))
        }
    }
}
