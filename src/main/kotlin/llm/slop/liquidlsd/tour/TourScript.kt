package llm.slop.liquidlsd.tour

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Tour script. Each step starts at [TourStep.t] seconds on the simulated clock.
 * Actions: move (ease cursor to target/x,y over durationSec), click (press at the cursor, release after durationSec),
 * set_page (UITheme.performancePageId = target), hold (caption only).
 */
@Serializable
data class TourStep(
    val t: Double,
    val action: String,
    val target: String? = null,
    val x: Float? = null,
    val y: Float? = null,
    val durationSec: Double? = null,
    val caption: String? = null
)

@Serializable
data class TourScript(
    val durationSec: Double,
    val steps: List<TourStep>
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): TourScript = json.decodeFromString(serializer(), text)
    }
}
