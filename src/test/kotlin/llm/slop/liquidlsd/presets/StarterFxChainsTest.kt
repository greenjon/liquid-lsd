package llm.slop.liquidlsd.presets

import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.FXChainDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Mixer.loadDefaultFxChains seeds Master FX and every deck from named bundled chain files, and
 * FX is session state only -- never part of a deck preset.
 */
class StarterFxChainsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun everyStarterChainFileShipsWithThreeSlots() {
        val starters = listOf(
            "subtle_optical_warmth.lsdfxchain", "liquid_mercury.lsdfxchain", "liquid_chrome_dimension.lsdfxchain",
            "hyperspace_trip.lsdfxchain", "prismatic_crystal_kaleidoscope.lsdfxchain"
        )
        for (name in starters) {
            val chain = json.decodeFromString<FXChainDto>(java.io.File("defaults/fx_chains", name).readText())
            assertEquals(3, chain.slots.size, name)
        }
    }

    @Test
    fun deckPresetsDoNotCarryFx() {
        // A preset saved while the FX chain was (briefly) embedded must load without it.
        val legacy = """{"name":"p","visualSourceType":"mandala","parameters":{},"feedbackParameters":{},"fxChain":{"name":"Old"}}"""
        val dto = json.decodeFromString<DeckPresetDto>(legacy)
        assertFalse(json.encodeToString(DeckPresetDto.serializer(), dto).contains("fxChain"))
    }
}
