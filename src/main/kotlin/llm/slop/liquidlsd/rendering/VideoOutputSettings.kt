package llm.slop.liquidlsd.rendering

import kotlinx.serialization.Serializable
import llm.slop.liquidlsd.ui.UITheme

/**
 * Supported video output endpoints in the Liquid LSD pipeline.
 */
enum class VideoOutputEndpoint(val displayName: String) {
    DECK_A("Deck A"),
    DECK_B("Deck B"),
    DECK_BG("Deck BG"),
    DECK_PV("Deck PV (Preview)"),
    MASTER("Master Output")
}

/**
 * Resolution modes for external video sharing.
 */
enum class OutputResolutionMode(val displayName: String) {
    SYNC_MASTER("Sync to Master"),
    RES_2160P("4K UHD (3840x2160)"),
    RES_1080P("1080p (1920x1080)"),
    RES_720P("720p (1280x720)"),
    RES_540P("540p (960x540)")
}

/**
 * Configuration for a single video output stream.
 */
@Serializable
data class VideoOutputConfig(
    val isEnabled: Boolean = false,
    val customName: String = "",
    val resolutionMode: OutputResolutionMode = OutputResolutionMode.SYNC_MASTER,
    val scalingMode: UITheme.OutputScaleMode = UITheme.OutputScaleMode.FIT
) {
    fun getEffectiveWidth(masterW: Int): Int = when (resolutionMode) {
        OutputResolutionMode.SYNC_MASTER -> masterW
        OutputResolutionMode.RES_2160P -> 3840
        OutputResolutionMode.RES_1080P -> 1920
        OutputResolutionMode.RES_720P -> 1280
        OutputResolutionMode.RES_540P -> 960
    }

    fun getEffectiveHeight(masterH: Int): Int = when (resolutionMode) {
        OutputResolutionMode.SYNC_MASTER -> masterH
        OutputResolutionMode.RES_2160P -> 2160
        OutputResolutionMode.RES_1080P -> 1080
        OutputResolutionMode.RES_720P -> 720
        OutputResolutionMode.RES_540P -> 540
    }
}
