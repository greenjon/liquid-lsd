package llm.slop.liquidlsd.presets

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.TransitionPlaylistDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransitionPlaylistSerializationTest {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testTransitionPlaylistDtoSerialization() {
        val playlist = TransitionPlaylistDto(
            version = 1,
            name = "Strobe & Wipes",
            tags = listOf("fast", "vj_set"),
            items = listOf(
                "radial_wipe",
                "library/transitions/luma_glitch.lsdtrans",
                "zoom_fade"
            )
        )

        val jsonStr = json.encodeToString(playlist)
        val decoded = json.decodeFromString<TransitionPlaylistDto>(jsonStr)

        assertEquals(1, decoded.version)
        assertEquals("Strobe & Wipes", decoded.name)
        assertEquals(listOf("fast", "vj_set"), decoded.tags)
        assertEquals(3, decoded.items.size)
        assertEquals("radial_wipe", decoded.items[0])
        assertEquals("library/transitions/luma_glitch.lsdtrans", decoded.items[1])
        assertEquals("zoom_fade", decoded.items[2])
    }

    @Test
    fun testTransitionPlaylistDefaults() {
        val jsonStr = """
            {
                "name": "Empty Playlist"
            }
        """.trimIndent()

        val decoded = json.decodeFromString<TransitionPlaylistDto>(jsonStr)
        assertEquals(1, decoded.version)
        assertEquals("Empty Playlist", decoded.name)
        assertTrue(decoded.tags.isEmpty())
        assertTrue(decoded.items.isEmpty())
    }
}
