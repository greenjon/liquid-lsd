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
        // The factory chains are web/fxchains/web-*.lsdfxchain, shipped without the prefix (build.gradle.kts prepareDefaultAssets).
        val starters = listOf("warmth", "mercury", "chrome", "hyperspace", "prism")
        for (name in starters) {
            val chain = json.decodeFromString<FXChainDto>(java.io.File("web/fxchains", "web-$name.lsdfxchain").readText())
            assertEquals(3, chain.slots.size, name)
        }
    }

    /** The first-run presets ship (as web/presets/web-<name>) and none plays a source the web TV counts as heavy. */
    @Test
    fun firstRunPresetsShipAndAreLight() {
        val heavy = Json.parseToJsonElement(java.io.File("web/settings.json").readText()).let {
            (it as kotlinx.serialization.json.JsonObject)["heavySources"].toString()
        }
        for ((slot, name) in SessionSerializer.FIRST_RUN_PRESETS) {
            val file = java.io.File("web/presets", "web-$name")
            kotlin.test.assertTrue(file.exists(), "$slot: $file missing")
            val source = json.decodeFromString<DeckPresetDto>(file.readText()).visualSourceType
            assertFalse("\"$source\"" in heavy, "$slot: $name plays heavy source $source")
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
