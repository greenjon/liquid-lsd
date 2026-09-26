package llm.slop.liquidlsd.ui

import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype.stbtt_FindGlyphIndex
import org.lwjgl.stb.STBTruetype.stbtt_InitFont
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every non-ASCII character in a UI string literal must be drawable: either an [Icons] constant
 * (baked from lucide.ttf via UITheme's ICON_RANGE) or a character inside [UITheme.MAIN_RANGES]
 * that Inter actually has a glyph for. Anything else renders as "?" -- e.g. a literal "⋮" (Inter
 * has no U+22EE even though it's in the Math range) or "⟲" (outside every baked range). Use an
 * [Icons] constant instead, adding one from lucide.ttf if needed.
 */
class UiGlyphCoverageTest {

    private fun loadFont(path: String): STBTTFontinfo {
        val bytes = UiGlyphCoverageTest::class.java.getResourceAsStream(path)?.readBytes() ?: fail("$path missing")
        // Kept referenced for the fontinfo's lifetime (stb reads from it lazily).
        fontBuffers += ByteBuffer.allocateDirect(bytes.size).put(bytes).flip()
        val info = STBTTFontinfo.create()
        assertTrue(stbtt_InitFont(info, fontBuffers.last()), "stbtt_InitFont failed for $path")
        return info
    }

    private val fontBuffers = mutableListOf<ByteBuffer>()

    @Test
    fun uiStringLiteralsOnlyUseBakedGlyphs() {
        val inter = loadFont("/fonts/Inter-Regular.ttf")
        val ranges = UITheme.MAIN_RANGES.toList().dropLast(1).map { it.toInt() and 0xFFFF }.chunked(2)
        val iconCodepoints = Icons::class.java.fields.mapNotNull { it.get(null) as? String }
            .flatMap { it.codePoints().toArray().toList() }.toSet()

        // U+FEFF: ISFParser strips a byte-order mark from shader source; never displayed.
        val nonDisplayed = setOf(0xFEFF)
        fun drawable(cp: Int) = cp in nonDisplayed || cp in iconCodepoints ||
            (ranges.any { (lo, hi) -> cp in lo..hi } && stbtt_FindGlyphIndex(inter, cp) != 0)

        val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        val unicodeEscape = Regex("\\\\u([0-9a-fA-F]{4})")
        val problems = mutableListOf<String>()
        File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                val trimmed = line.trimStart()
                // Comments/KDoc and console output never reach ImGui.
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed
                if ("println(" in line || "logger." in line) return@forEachIndexed
                for (m in literal.findAll(line)) {
                    val text = unicodeEscape.replace(m.groupValues[1]) { it.groupValues[1].toInt(16).toChar().toString() }
                    text.codePoints().filter { it >= 0x80 && !drawable(it) }.forEach { cp ->
                        problems += "${file.path}:${i + 1}: '${String(Character.toChars(cp))}' (U+%04X)".format(cp)
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "Glyphs that would render as '?':\n" + problems.joinToString("\n"))
    }
}
