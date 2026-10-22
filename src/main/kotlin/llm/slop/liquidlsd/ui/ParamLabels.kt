package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.rendering.isf.ISFHeader

/**
 * Display names for shader parameters: the ISF input's LABEL when it has one, else the NAME made readable
 * (`CellScale` -> "Cell scale", `rotateZ` -> "Rotate Z", `rgb_shift` -> "RGB shift"). Display text only: parameter
 * keys, paths, presets and MIDI/OSC addresses keep the raw NAME.
 */
object ParamLabels {

    /** Lower-case words that read as acronyms. */
    private val ACRONYMS = setOf("fx", "rgb", "rgba", "hsv", "hsl", "lfo", "uv", "xy", "xyz", "fft", "bpm", "id", "ui", "rf", "yc")

    /** Suffixes an ISF point2D / color input adds to its NAME, one parameter per component. */
    private val COMPONENT_SUFFIXES = setOf("X", "Y", "R", "G", "B", "A")

    private val WORD = Regex("""\d+[A-Z]*(?![a-z])|[A-Z]+(?![a-z])|[A-Z]?[a-z]+|\d+""")

    /** [name]'s display label in [header] (null header: just [humanize]). */
    fun of(header: ISFHeader?, name: String): String {
        header?.INPUTS.orEmpty().firstOrNull { it.NAME == name }?.let { input ->
            return input.LABEL?.takeIf { it.isNotBlank() } ?: humanize(name)
        }
        // A component parameter ("center X") labels as its input plus the component.
        val base = name.substringBeforeLast(' ', "")
        val suffix = name.substringAfterLast(' ')
        if (base.isNotEmpty() && suffix in COMPONENT_SUFFIXES && header?.INPUTS.orEmpty().any { it.NAME == base }) {
            return "${of(header, base)} $suffix"
        }
        return humanize(name)
    }

    /**
     * Splits camelCase and snake_case into sentence-case words, keeping acronyms and single letters upper case.
     * A name that already has a space is taken as written ("Hue Sweep", "Recipe Select").
     */
    fun humanize(name: String): String = humanized.getOrPut(name) { split(name) }

    /** Per-name results, so the per-frame caption path allocates nothing once a name has been seen. */
    private val humanized = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun split(name: String): String {
        if (name.isBlank() || ' ' in name.trim()) return name
        val words = mutableListOf<String>()
        for (w in name.split('_', '-').flatMap { chunk -> WORD.findAll(chunk).map { it.value }.toList() }) {
            // A single letter and its number read as one name ("p1" -> "P1", not "P 1").
            if (words.isNotEmpty() && words.last().length == 1 && words.last()[0].isLetter() && w.all { it.isDigit() }) words[words.size - 1] = words.last() + w
            else words.add(w)
        }
        if (words.isEmpty()) return name
        return words.mapIndexed { i, w ->
            when {
                w.none { it.isLowerCase() } -> w
                w.lowercase() in ACRONYMS -> w.uppercase()
                w.length == 1 -> w.uppercase()
                i == 0 -> w.lowercase().replaceFirstChar { it.uppercaseChar() }
                else -> w.lowercase()
            }
        }.joinToString(" ")
    }
}
