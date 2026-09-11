package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.models.ClipboardManager
import llm.slop.liquidlsd.models.CellClipboardData
import llm.slop.liquidlsd.models.RowClipboardData
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ModulationOperator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParametersClipboardTest {

    @Test
    fun testGetModsForCellExtraction() {
        val param = ModulatableParameter(baseValue = 0.5f, minClamp = 0f, maxClamp = 1f)
        val lfoMod = CvModulator(sourceId = "lfo", depth = 0.4f)
        val audioAmpMod = CvModulator(sourceId = "audio_amp", depth = 0.7f)
        val audioBassMod = CvModulator(sourceId = "audio_bass", depth = 0.3f)
        val audioFluxMod = CvModulator(sourceId = "audio_flux_bass", depth = 0.9f)
        val midiMod = CvModulator(sourceId = "midi_cc_7", depth = 0.5f)

        param.modulators.addAll(listOf(lfoMod, audioAmpMod, audioBassMod, audioFluxMod, midiMod))

        // Value / Final / Base cell should yield empty modulators list (since it represents base value/row)
        assertEquals(0, ParametersKeyboard.getModsForCell(param, "value").size)
        assertEquals(0, ParametersKeyboard.getModsForCell(param, "final").size)
        assertEquals(0, ParametersKeyboard.getModsForCell(param, "base").size)

        // LFO cell
        val lfoExtracted = ParametersKeyboard.getModsForCell(param, "lfo")
        assertEquals(1, lfoExtracted.size)
        assertEquals("lfo", lfoExtracted[0].sourceId)

        // Audio cell should extract all audio sources (both RMS and Flux)
        val audioExtracted = ParametersKeyboard.getModsForCell(param, "audio")
        assertEquals(3, audioExtracted.size)
        assertTrue(audioExtracted.any { it.sourceId == "audio_amp" })
        assertTrue(audioExtracted.any { it.sourceId == "audio_bass" })
        assertTrue(audioExtracted.any { it.sourceId == "audio_flux_bass" })

        // MIDI cell should extract midi CC sources
        val midiExtracted = ParametersKeyboard.getModsForCell(param, "midi")
        assertEquals(1, midiExtracted.size)
        assertEquals("midi_cc_7", midiExtracted[0].sourceId)
    }

    @Test
    fun testAudioAndTriggerClipboardCopyPaste() {
        val srcParam = ModulatableParameter(baseValue = 0.2f, minClamp = 0f, maxClamp = 1f)
        val audioMod = CvModulator(sourceId = "audio_mid", depth = 0.8f, operator = ModulationOperator.ADD)
        srcParam.modulators.add(audioMod)

        val audioMods = ParametersKeyboard.getModsForCell(srcParam, "audio")
        ClipboardManager.cellClipboard = CellClipboardData(
            sourceParamKey = "Deck A/Geometry/L1",
            sourceCvId = "audio",
            modulators = audioMods.map { it.toDto() }
        )

        val destParam = ModulatableParameter(baseValue = 0.5f, minClamp = 0f, maxClamp = 1f)
        destParam.modulators.add(CvModulator(sourceId = "audio_amp", depth = 0.1f))

        // Apply audio cell clipboard to destination parameter
        ClipboardManager.applyCellClipboard(destParam, "audio", ClipboardManager.cellClipboard!!)

        // Old audio modulator should be replaced with pasted audio_mid
        assertEquals(1, destParam.modulators.size)
        assertEquals("audio_mid", destParam.modulators[0].sourceId)
        assertEquals(0.8f, destParam.modulators[0].depth)
    }

    @Test
    fun testMidiClipboardCopyPaste() {
        val srcParam = ModulatableParameter(baseValue = 0.3f, minClamp = 0f, maxClamp = 1f)
        val midiMod = CvModulator(sourceId = "midi_cc_16", depth = 0.6f)
        srcParam.modulators.add(midiMod)

        val midiMods = ParametersKeyboard.getModsForCell(srcParam, "midi")
        ClipboardManager.cellClipboard = CellClipboardData(
            sourceParamKey = "Deck A/Geometry/L2",
            sourceCvId = "midi",
            modulators = midiMods.map { it.toDto() }
        )

        val destParam = ModulatableParameter(baseValue = 0.4f, minClamp = 0f, maxClamp = 1f)
        destParam.modulators.add(CvModulator(sourceId = "midi_cc_1", depth = 0.2f))

        ClipboardManager.applyCellClipboard(destParam, "midi", ClipboardManager.cellClipboard!!)

        assertEquals(1, destParam.modulators.size)
        assertEquals("midi_cc_16", destParam.modulators[0].sourceId)
        assertEquals(0.6f, destParam.modulators[0].depth)
    }
}
