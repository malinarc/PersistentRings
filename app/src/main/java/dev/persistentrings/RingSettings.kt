package dev.persistentrings

enum class LedPattern(val storageValue: String, val title: String, val description: String) {
    SINGLE_FLASH("single_flash", "Single flash", "One pulse using the selected duration"),
    DOUBLE_FLASH("double_flash", "Double flash", "Two pulses with a short pause");

    companion object {
        fun fromStorage(value: String?, legacyDoubleFlash: Boolean = false): LedPattern = when (value) {
            DOUBLE_FLASH.storageValue -> DOUBLE_FLASH
            null -> if (legacyDoubleFlash) DOUBLE_FLASH else SINGLE_FLASH
            else -> SINGLE_FLASH
        }
    }
}

data class RingSettings(
    val enabled: Boolean = false,
    val intervalSeconds: Int = 5,
    val durationMs: Int = 500,
    val color: Int = 0xFF0000,
    val pattern: LedPattern = LedPattern.SINGLE_FLASH,
    val preciseTiming: Boolean = true,
    val packages: Set<String> = emptySet(),
    val verifiedFirmware: String = "",
    val faceDownOnly: Boolean = false,
    val appColors: Map<String, Int> = emptyMap()
) {
    fun colorFor(packageName: String): Int = (appColors[packageName] ?: color) and 0xFFFFFF
}
