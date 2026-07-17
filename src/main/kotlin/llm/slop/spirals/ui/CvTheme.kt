package llm.slop.spirals.ui

import imgui.ImGui

object CvTheme {
    fun getThemeColor(cvId: String, alpha: Float = 1f): Int {
        return when (cvId) {
            "final"          -> ImGui.colorConvertFloat4ToU32(0.0f, 1.0f, 0.7f, alpha)
            "base"           -> ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, alpha)
            "midi"           -> ImGui.colorConvertFloat4ToU32(0.7f, 0.3f, 1.0f, alpha)
            "lfo"            -> ImGui.colorConvertFloat4ToU32(0.0f, 0.7f, 1.0f, alpha)
            "sampleAndHold"  -> ImGui.colorConvertFloat4ToU32(0.7f, 0.4f, 1.0f, alpha)
            "beatPhase"      -> ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 1.0f, alpha)
            "audio"          -> ImGui.colorConvertFloat4ToU32(0.3f, 0.9f, 0.0f, alpha)
            "amp", "audio_amp" -> ImGui.colorConvertFloat4ToU32(0.3f, 0.9f, 0.0f, alpha)
            "bass", "audio_bass" -> ImGui.colorConvertFloat4ToU32(0.9f, 0.2f, 0.2f, alpha)
            "mid", "audio_mid" -> ImGui.colorConvertFloat4ToU32(0.9f, 0.5f, 0.1f, alpha)
            "high", "audio_high" -> ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.2f, alpha)
            "trigger"        -> ImGui.colorConvertFloat4ToU32(1.0f, 0.0f, 0.5f, alpha)
            "onset", "trigger_onset" -> ImGui.colorConvertFloat4ToU32(1.0f, 0.3f, 0.6f, alpha)
            "accent", "trigger_accent" -> ImGui.colorConvertFloat4ToU32(0.9f, 0.1f, 0.9f, alpha)
            else             -> ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, alpha)
        }
    }

    fun getThemeColorRGB(cvId: String): FloatArray {
        return when (cvId) {
            "final"          -> floatArrayOf(0.0f, 1.0f, 0.7f)
            "base"           -> floatArrayOf(0.8f, 0.6f, 0.2f)
            "midi"           -> floatArrayOf(0.7f, 0.3f, 1.0f)
            "lfo"            -> floatArrayOf(0.0f, 0.7f, 1.0f)
            "sampleAndHold"  -> floatArrayOf(0.7f, 0.4f, 1.0f)
            "beatPhase"      -> floatArrayOf(0.4f, 0.4f, 1.0f)
            "audio"          -> floatArrayOf(0.3f, 0.9f, 0.0f)
            "amp", "audio_amp" -> floatArrayOf(0.3f, 0.9f, 0.0f)
            "bass", "audio_bass" -> floatArrayOf(0.9f, 0.2f, 0.2f)
            "mid", "audio_mid" -> floatArrayOf(0.9f, 0.5f, 0.1f)
            "high", "audio_high" -> floatArrayOf(0.9f, 0.9f, 0.2f)
            "trigger"        -> floatArrayOf(1.0f, 0.0f, 0.5f)
            "onset", "trigger_onset" -> floatArrayOf(1.0f, 0.3f, 0.6f)
            "accent", "trigger_accent" -> floatArrayOf(0.9f, 0.1f, 0.9f)
            else             -> floatArrayOf(0.5f, 0.5f, 0.5f)
        }
    }
}
