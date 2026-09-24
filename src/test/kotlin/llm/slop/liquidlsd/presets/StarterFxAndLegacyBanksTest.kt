package llm.slop.liquidlsd.presets

import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.FXBankDto
import llm.slop.liquidlsd.models.MixerDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * FX1/FX2 banks were removed. Mixer.loadDefaultFxBanks now seeds each deck's starter chain
 * straight from chains 0 and 1 of two bundled bank files, and old sessions' fxBank1/fxBank2
 * are still decoded so SessionSerializer can migrate them into per-deck chains.
 */
class StarterFxAndLegacyBanksTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun starterBankFilesHaveTheTwoChainsEachDeckIsSeededFrom() {
        for (name in listOf("psychedelic_warp_and_flow.lsdfxbank", "liquid_chrome_and_prisms.lsdfxbank")) {
            val bank = json.decodeFromString<FXBankDto>(java.io.File("defaults/fx_banks", name).readText())
            assertNotNull(bank.chains.getOrNull(0), "$name chain 0")
            assertNotNull(bank.chains.getOrNull(1), "$name chain 1")
        }
    }

    @Test
    fun legacySessionFxBanksStillDecodeAndNewSessionsOmitThem() {
        val crossfade = """{"baseValue":0.5,"baseMin":0,"baseMax":1,"randomizeBase":false,"modulators":[]}"""
        val legacy = """{"crossfade":$crossfade,"fxBank1":{"name":"FX1","chains":[{"name":"Old A"},{"name":"Old B"}]},"fxBank2":{"name":"FX2","chains":[{"name":"Old BG"}]}}"""
        val dto = json.decodeFromString<MixerDto>(legacy)
        assertEquals("Old A", dto.fxBank1?.chains?.getOrNull(0)?.name)
        assertEquals("Old BG", dto.fxBank2?.chains?.getOrNull(0)?.name)

        val fresh = json.decodeFromString<MixerDto>("""{"crossfade":$crossfade}""")
        assertNull(fresh.fxBank1)
        assertNull(fresh.fxBank2)
    }
}
