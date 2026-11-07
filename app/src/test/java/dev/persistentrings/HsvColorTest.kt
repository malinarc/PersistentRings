package dev.persistentrings

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class HsvColorTest {
    @Test fun primaryAndSecondaryColorsAreExact() {
        val expected = listOf(0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0xFF00FF)
        expected.forEachIndexed { i, rgb ->
            assertEquals(rgb, HsvColor(i * 60f, 1f).toRgb())
            val angle = i * PI / 3
            assertEquals(rgb, HsvColor.at(cos(angle).toFloat()*100, sin(angle).toFloat()*100, 100f).toRgb())
            assertEquals(rgb, HsvColor.fromRgb(rgb).toRgb())
        }
    }
    @Test fun centerIsWhiteAndEdgeIsFullySaturated() {
        assertEquals(0xFFFFFF, HsvColor.at(0f, 0f, 100f).toRgb())
        assertEquals(0xFF0000, HsvColor.at(100f, 0f, 100f).toRgb())
        assertEquals(0xFF0000, HsvColor.at(200f, 0f, 100f).toRgb())
        assertEquals(0xFF8080, HsvColor(0f, 0.5f).toRgb())
    }
    @Test fun hueWrapsAndSaturationClamps() {
        assertEquals(0xFF0000, HsvColor(360f, 1f).toRgb())
        assertEquals(0xFF00FF, HsvColor(-60f, 1f).toRgb())
        assertEquals(0xFFFFFF, HsvColor(100f, -1f).toRgb())
    }
}
