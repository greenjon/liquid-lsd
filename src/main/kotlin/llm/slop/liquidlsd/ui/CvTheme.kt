package llm.slop.liquidlsd.ui

import imgui.ImGui

object CvTheme {
    fun getThemeColor(cvId: String, alpha: Float = 1f): Int {
        val rgb = getThemeColorRGB(cvId)
        return ImGui.colorConvertFloat4ToU32(rgb[0], rgb[1], rgb[2], alpha)
    }

    fun getThemeColorRGB(cvId: String): FloatArray {
        return when (cvId) {
            // Base Value / Static Output (Mint Cyan)
            "value", "final"         -> floatArrayOf(0.00f, 0.95f, 0.72f) // Crisp Mint Cyan
            "base"                   -> floatArrayOf(0.85f, 0.65f, 0.35f) // Warm Bronze Sand

            // MIDI (Electric Violet / Bright Orchid)
            "midi"                   -> floatArrayOf(0.72f, 0.45f, 1.00f) // Bright Orchid Purple

            // LFO / Synthetic Generators (Electric Sky Blue)
            "lfo"                    -> floatArrayOf(0.15f, 0.75f, 1.00f) // Electric Sky Blue
            "sampleAndHold"          -> floatArrayOf(0.30f, 0.65f, 1.00f) // Periwinkle Blue
            "beatPhase", "beatSine"  -> floatArrayOf(0.40f, 0.60f, 1.00f) // Deep Sky Blue

            // Step Sequencer (Electric Lime / Acid Green)
            "seq"                    -> floatArrayOf(0.20f, 0.95f, 0.30f) // Electric Lime Green

            // Audio Spectrum Followers (Warm Amber / Gold)
            "audio"                  -> floatArrayOf(1.00f, 0.68f, 0.12f) // Warm Amber Gold
            "amp", "audio_amp"       -> floatArrayOf(1.00f, 0.75f, 0.20f) // Bright Amber
            "bass", "audio_bass"     -> floatArrayOf(1.00f, 0.42f, 0.15f) // Deep Orange
            "mid", "audio_mid"       -> floatArrayOf(1.00f, 0.68f, 0.12f) // Golden Amber
            "high", "audio_high"     -> floatArrayOf(0.95f, 0.88f, 0.25f) // Bright Gold

            // Transient Triggers (Hot Coral Rose)
            "trigger"                -> floatArrayOf(1.00f, 0.25f, 0.50f) // Hot Coral Rose
            "onset", "trigger_onset" -> floatArrayOf(1.00f, 0.35f, 0.55f) // Coral Pink
            "accent", "trigger_accent"->floatArrayOf(1.00f, 0.20f, 0.40f) // Crimson Rose

            else                     -> floatArrayOf(0.60f, 0.60f, 0.60f)
        }
    }
}

