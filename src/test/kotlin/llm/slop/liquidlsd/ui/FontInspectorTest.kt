package llm.slop.liquidlsd.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype.*
import java.nio.ByteBuffer
import kotlin.collections.iterator

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
            "PREFERENCES" to 0xe154,
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
            "EJECT" to 0xe45d,
            "UPLOAD" to 0xe19e,
            "RECTANGLE_VERTICAL" to 0xe377,
            "ROWS_2" to 0xe439,
            "PANEL_BOTTOM" to 0xe42c,
            "PANEL_LEFT_OPEN" to 0xe21d,
            "WAVE_SINE" to 0xe38b,
            "WAVE_TRI" to 0xe192,
            "WAVE_SQUARE" to 0xe167,
            "ALIGN_LEFT_LINE" to 0xe457,
            "ALIGN_CENTER_LINE" to 0xe43b,
            "ALIGN_RIGHT_LINE" to 0xe459,
            "LOCK" to 0xe10b,
            "UNLOCK" to 0xe10c,
            "LOCK_OPEN" to 0xe10c,
            "LINK" to 0xe102,
            "UNLINK" to 0xe19c,
            "POWER_OFF" to 0xe209,
            "VOLUME" to 0xe1a9,
            "VOLUME_X" to 0xe1ac,
            "FILE_PLUS" to 0xe0c9,
            "FOLDER_PLUS" to 0xe0d9,
            "CHEVRON_DOWN" to 0xe06d,
            "DOWNLOAD" to 0xe0b2,
            "NOTE" to 0xe1f9,
            "BOT" to 0xe1bb,
            "BOT_OFF" to 0xe5e0,
            "X" to 0xe1b2,
            "SQUARE" to 0xe167,
            "COPY" to 0xe09e,
            "MAXIMIZE" to 0xe112,
            "MINIMIZE" to 0xe11a,
            "MORE_HORIZONTAL" to 0xe0b6,
            "MORE_VERTICAL" to 0xe0b7
        )

        for ((name, codepoint) in codepointsToCheck) {
            val glyphIndex = stbtt_FindGlyphIndex(fontInfo, codepoint)
            assertTrue(glyphIndex != 0, "Icon $name (0x${Integer.toHexString(codepoint).uppercase()}) was not found in the font!")
        }

        val fontMethods = imgui.ImFont::class.java.methods.map { it.name }.sorted()
        println("ImFont methods: $fontMethods")
        val drawListMethods = imgui.ImDrawList::class.java.methods.filter { it.name.contains("addText") }
        println("ImDrawList addText methods: $drawListMethods")
        assertTrue(true)
    }
}
