package llm.slop.spirals.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype.*
import java.nio.ByteBuffer

class FontInspectorTest {

    @Test
    fun testInspectFont() {
        val resourceStream = FontInspectorTest::class.java.getResourceAsStream("/fonts/lucide.ttf")
        if (resourceStream == null) {
            println("lucide.ttf not found in resources!")
            return
        }
        val fontData = resourceStream.readBytes()
        println("Font data size: ${fontData.size} bytes")

        val fontInfo = STBTTFontinfo.create()
        val buffer = ByteBuffer.allocateDirect(fontData.size)
        buffer.put(fontData)
        buffer.flip()

        val success = stbtt_InitFont(fontInfo, buffer)
        assertTrue(success, "Failed to initialize font info with stbtt_InitFont")

        println("Checking specific codepoints from staged Icons.kt...")
        val codepointsToCheck = mapOf(
            "SETTINGS" to 0xe154,
            "POWER" to 0xe140,
            "TRASH" to 0xe18e,
            "DICES" to 0xe2c5,
            "FOLDER" to 0xe0d7,
            "FILE" to 0xe0c0,
            "ACTIVITY" to 0xe038,
            "ZAP" to 0xe1b4,
            "CHEVRON_UP" to 0xe070,
            "SEARCH" to 0xe151,
            "REFRESH" to 0xe145,
            "PLUS" to 0xe13d,
            "MINUS" to 0xe11c,
            "PLAY" to 0xe13c,
            "PAUSE" to 0xe12e,
            "ALERT" to 0xe193,
            "INFO" to 0xe0f9,
            "SAVE" to 0xe14d,
            "UPLOAD" to 0xe19e,
            "RECTANGLE_VERTICAL" to 0xe377,
            "ROWS_2" to 0xe439,
            "PANEL_BOTTOM" to 0xe42c,
            "PANEL_LEFT_OPEN" to 0xe21d,
            "WAVE_SINE" to 0xe38b,
            "WAVE_TRI" to 0xe192,
            "WAVE_SQUARE" to 0xe167,
            "ALIGN_LEFT_LINE" to 0xe457,
            "ALIGN_CENTER_LINE" to 0xe5cf,
            "ALIGN_RIGHT_LINE" to 0xe459
        )

        for ((name, codepoint) in codepointsToCheck) {
            val glyphIndex = stbtt_FindGlyphIndex(fontInfo, codepoint)
            assertTrue(glyphIndex != 0, "Icon $name (0x${Integer.toHexString(codepoint).uppercase()}) was not found in the font!")
        }

        println("Scanning Private Use Area (0xE000 - 0xF8FF) for valid glyphs...")
        var foundCount = 0
        for (codepoint in 0xe000..0xf8ff) {
            val glyphIndex = stbtt_FindGlyphIndex(fontInfo, codepoint)
            if (glyphIndex != 0) {
                foundCount++
            }
        }
        println("Total glyphs found in PUA: $foundCount")
        assertTrue(foundCount > 0, "No glyphs found in PUA range")
    }
}
