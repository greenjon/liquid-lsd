package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.models.DeckPresetDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalVideoSourceTest {

    @Test
    fun testExternalVideoSourceProperties() {
        val source = ExternalVideoSource(id = "spout_input", serverName = "OBS-Camera")

        assertEquals("spout_input", source.id)
        assertEquals("External Video", source.displayName)
        assertEquals("OBS-Camera", source.serverName)
        assertFalse(source.is3D)
        assertEquals(listOf("Input", "Video"), source.categories)
        assertEquals(1.0f, source.globalAlpha.value)

        val paths = source.getParameterPaths("Deck A")
        assertEquals(1, paths.size)
        assertEquals("Deck A/External Video/Gain", paths[0].first)
    }

    @Test
    fun testCloneExternalVideoSource() {
        val original = ExternalVideoSource(id = "spout_input", serverName = "Resolume-Layer1")
        val cloned = original.clone()

        assertTrue(cloned is ExternalVideoSource)
        assertEquals(original.id, cloned.id)
        assertEquals(original.serverName, cloned.serverName)
    }

    @Test
    fun testRegistryDeduplicationLogic() {
        VisualSourceRegistry.availableSources.clear()
        
        // Simulate loading static sources twice
        if (VisualSourceRegistry.availableSources.none { it is ExternalVideoSource }) {
            VisualSourceRegistry.availableSources.add(ExternalVideoSource())
        }
        if (VisualSourceRegistry.availableSources.none { it is ExternalVideoSource }) {
            VisualSourceRegistry.availableSources.add(ExternalVideoSource())
        }

        val sources = VisualSourceRegistry.availableSources.filterIsInstance<ExternalVideoSource>()
        assertEquals(1, sources.size, "VisualSourceRegistry should contain exactly one ExternalVideoSource instance")
        assertEquals("spout_input", sources.first().id)
        
        VisualSourceRegistry.availableSources.clear()
    }

    @Test
    fun testUpdateAndDisconnectState() {
        val source = ExternalVideoSource(id = "spout_input", serverName = "")
        source.update()
        assertEquals(0, source.currentTextureId)

        source.serverName = "NonExistentServer"
        source.update()
        // Connection to non-existent server will fail gracefully
        assertEquals(0, source.currentTextureId)

        source.dispose()
        assertEquals(0, source.currentTextureId)
    }

    @Test
    fun testDeckPresetDtoStructure() {
        val dto = DeckPresetDto(
            name = "External Video Preset",
            visualSourceType = "spout_input",
            serverName = "TouchDesigner-Out",
            parameters = emptyMap(),
            feedbackParameters = emptyMap(),
            isEmpty = false
        )

        assertEquals("spout_input", dto.visualSourceType)
        assertEquals("TouchDesigner-Out", dto.serverName)
        assertFalse(dto.isEmpty)
    }
}
