package llm.slop.liquidlsd.parameters

import kotlinx.serialization.Serializable

@Serializable
enum class ModulationOperator {
    ADD, MUL, SCALE
}

@Serializable
enum class Waveform {
    SINE, TRIANGLE, SQUARE, RANDOM
}

@Serializable
enum class LfoSpeedMode {
    SLOW, MEDIUM, FAST
}

@Serializable
enum class GenUnit {
    TIME, BEAT, FRAME
}

@Serializable
enum class GeneratorModMode {
    NONE, AM, PM, ADD
}

@Serializable
enum class ScopeTimebase(
    val label: String,
    val durationSec: Float,
    val divSec: Float
) {
    AUTO("Auto", 0f, 0f),
    ONE_SEC("1s", 1.0f, 0.25f),
    TEN_SEC("10s", 10.0f, 2.0f),
    HUNDRED_SEC("100s", 100.0f, 20.0f),
    FIFTEEN_MIN("15m", 900.0f, 180.0f),
    TWO_POINT_FIVE_HOURS("2.5h", 9000.0f, 1800.0f),
    TWENTY_FOUR_HOURS("24h", 86400.0f, 14400.0f);

    companion object {
        fun formatTimeOffset(sec: Float): String {
            val absSec = kotlin.math.abs(sec)
            val sign = if (sec < -0.001f) "-" else if (sec > 0.001f) "+" else ""
            return when {
                absSec < 0.001f -> "NOW"
                absSec < 1.0f -> "${sign}${"%.0f".format(absSec * 1000f)}ms"
                absSec < 60.0f -> {
                    if (absSec % 1.0f < 0.01f || absSec >= 10.0f) "${sign}${"%.0f".format(absSec)}s"
                    else "${sign}${"%.1f".format(absSec)}s"
                }
                absSec < 3600.0f -> {
                    val mins = absSec / 60.0f
                    if (mins % 1.0f < 0.05f) "${sign}${"%.0f".format(mins)}m"
                    else "${sign}${"%.1f".format(mins)}m"
                }
                else -> {
                    val hrs = absSec / 3600.0f
                    if (hrs % 1.0f < 0.05f) "${sign}${"%.0f".format(hrs)}h"
                    else "${sign}${"%.1f".format(hrs)}h"
                }
            }
        }
    }
}

@Serializable
enum class AudioFollowerMode(
    val label: String,
    val defaultAttackMs: Float,
    val defaultDecayMs: Float
) {
    RAW("Raw (Instant Jitter)", 0f, 0f),
    PUNCHY("Punchy (Fast)", 5f, 150f),
    SMOOTH("Smooth Swell", 40f, 400f),
    SLOW("Slow Pulse", 100f, 800f),
    AMBIENT("Ambient Drift", 250f, 1500f),
    CUSTOM("Custom", 0f, 100f);
}

