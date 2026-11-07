package dev.persistentrings

import kotlin.math.*

/** Full-value HSV: clockwise hue from red at the right edge, white at the center. */
data class HsvColor(val hue: Float, val saturation: Float) {
    fun toRgb(): Int {
        val h = ((hue % 360f + 360f) % 360f) / 60f
        val s = saturation.coerceIn(0f, 1f)
        val x = s * (1f - abs(h % 2f - 1f))
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(s, x, 0f)
            1 -> Triple(x, s, 0f)
            2 -> Triple(0f, s, x)
            3 -> Triple(0f, x, s)
            4 -> Triple(x, 0f, s)
            else -> Triple(s, 0f, x)
        }
        val m = 1f - s
        fun channel(v: Float) = ((v + m) * 255f).roundToInt().coerceIn(0, 255)
        return (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }
    companion object {
        fun at(dx: Float, dy: Float, radius: Float): HsvColor {
            require(radius > 0f)
            val hue = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
            return HsvColor(hue, (hypot(dx, dy) / radius).coerceIn(0f, 1f))
        }
        fun fromRgb(rgb: Int): HsvColor {
            val r = ((rgb shr 16) and 255) / 255f
            val g = ((rgb shr 8) and 255) / 255f
            val b = (rgb and 255) / 255f
            val max = maxOf(r, g, b)
            val delta = max - minOf(r, g, b)
            val hue = when {
                delta == 0f -> 0f
                max == r -> 60f * ((g - b) / delta)
                max == g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }
            return HsvColor((hue + 360f) % 360f, if (max == 0f) 0f else delta / max)
        }
    }
}
