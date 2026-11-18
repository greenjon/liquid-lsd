package llm.slop.liquidlsd.parameters

import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.ModulatorDto
import llm.slop.liquidlsd.models.toDomain
import llm.slop.liquidlsd.models.toDto
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Min/Max over Base/Amount, the two roll spaces, and loading files saved before roll spaces existed. */
class ModulatorRollSpaceTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val eps = 1e-5f

    @Test
    fun endsRollEachEndWithinItsOwnRange() {
        val mod = CvModulator(sourceId = "lfo", depth = 0.5f, dcOffset = 0f, rollSpace = RollSpace.ENDS,
            randomizeLfoMin = true, lfoMinRollMin = -0.8f, lfoMinRollMax = -0.6f,
            randomizeLfoMax = true, lfoMaxRollMin = 0.2f, lfoMaxRollMax = 0.4f)
        val rnd = Random(7)
        repeat(200) {
            val r = mod.rollRange(rnd)
            assertTrue(r.getLfoMin() in -0.8f - eps..-0.6f + eps, "min ${r.getLfoMin()}")
            assertTrue(r.getLfoMax() in 0.2f - eps..0.4f + eps, "max ${r.getLfoMax()}")
        }
    }

    @Test
    fun endsPinnedEndStays() {
        val mod = CvModulator(sourceId = "lfo", depth = 0.5f, dcOffset = 0.1f, rollSpace = RollSpace.ENDS,
            randomizeLfoMin = false, lfoMinRollMin = -1f, lfoMinRollMax = 1f,
            randomizeLfoMax = true, lfoMaxRollMin = 0.7f, lfoMaxRollMax = 0.9f)
        val rnd = Random(3)
        repeat(100) { assertEquals(-0.4f, mod.rollRange(rnd).getLfoMin(), eps) }
    }

    @Test
    fun baseAmountWithBasePinnedNeverMovesBase() {
        val mod = CvModulator(sourceId = "lfo", depth = 0.3f, dcOffset = 0.25f, rollSpace = RollSpace.BASE_AMOUNT,
            randomizeDcOffset = false, dcOffsetMin = -1f, dcOffsetMax = 1f,
            randomizeDepth = true, depthMin = 0.1f, depthMax = 0.9f)
        val rnd = Random(11)
        var varied = false
        repeat(100) {
            val r = mod.rollRange(rnd)
            assertEquals(0.25f, r.dcOffset, eps)
            assertTrue(r.depth in 0.1f..0.9f)
            if (kotlin.math.abs(r.depth - 0.3f) > 0.01f) varied = true
        }
        assertTrue(varied)
    }

    @Test
    fun minMaxRoundTripsForBothPolarities() {
        val bip = CvModulator(sourceId = "lfo").withLfoRange(-0.2f, 0.6f)
        assertEquals(0.4f, bip.depth, eps); assertEquals(0.2f, bip.dcOffset, eps)
        assertEquals(-0.2f, bip.getLfoMin(), eps); assertEquals(0.6f, bip.getLfoMax(), eps)

        val uni = CvModulator(sourceId = "audio_bass").withLfoRange(-0.2f, 0.6f)
        assertEquals(0.8f, uni.depth, eps); assertEquals(-0.2f, uni.dcOffset, eps)
        assertEquals(-0.2f, uni.getLfoMin(), eps); assertEquals(0.6f, uni.getLfoMax(), eps)
    }

    @Test
    fun sequencerPolarityFollowsTheParameter() {
        val seq = CvModulator(sourceId = "seq", depth = 1f, dcOffset = 0f)
        assertTrue(seq.unipolarOn(paramBipolar = false))
        assertFalse(seq.unipolarOn(paramBipolar = true))
        // Unipolar step grid: steps 0..1 land on 0..1.
        assertEquals(0f, seq.getLfoMin(seq.unipolarOn(false)), eps)
        // Bipolar step grid: steps -1..1 land on -1..1.
        assertEquals(-1f, seq.getLfoMin(seq.unipolarOn(true)), eps)
    }

    @Test
    fun accessorWritesBaseAndAmountUnclampedAndMinMaxByParamPolarity() {
        val param = ModulatableParameter(baseValue = 0.5f, minClamp = 0f, maxClamp = 1f)
        val seq = CvModulator(sourceId = "seq", depth = 1f, dcOffset = 0f)
        ModulatorPropertyAccessor.set(seq, "lfoMax", 0.5f, param)
        assertEquals(0f, seq.dcOffset, eps); assertEquals(0.5f, seq.depth, eps)
        assertEquals(0.5f, ModulatorPropertyAccessor.get(seq, "lfoMax", param)!!, eps)

        val lfo = CvModulator(sourceId = "lfo", depth = 0.5f, dcOffset = 0.8f)
        ModulatorPropertyAccessor.set(lfo, "depth", 1.5f)
        ModulatorPropertyAccessor.set(lfo, "dcOffset", 1.2f)
        assertEquals(1.5f, lfo.depth, eps); assertEquals(1.2f, lfo.dcOffset, eps)
    }

    @Test
    fun switchingRollSpaceCarriesTheDiceState() {
        val on = CvModulator(sourceId = "lfo", depth = 0.5f).withRangeRolling(true)
        assertEquals(RollSpace.ENDS, on.rollSpace)
        assertTrue(on.randomizeLfoMin && on.randomizeLfoMax)
        val switched = on.withRollSpace(RollSpace.BASE_AMOUNT)
        assertTrue(switched.randomizeDepth && switched.randomizeDcOffset)
        assertFalse(switched.randomizeLfoMin || switched.randomizeLfoMax)
        assertTrue(switched.hasRangeVariation)

        val off = CvModulator(sourceId = "lfo", depth = 0.5f).withRollSpace(RollSpace.BASE_AMOUNT)
        assertFalse(off.rollsRange)
    }

    @Test
    fun legacyLfoRangesMoveToEnds() {
        // Saved by the old Min/Max editor: Min end roll range in dcOffsetMin/Max, Max end in depthMin/Max.
        val legacy = """{"sourceId":"lfo","operator":"ADD","depth":0.5,"dcOffset":0.0,
            "randomizeDcOffset":true,"dcOffsetMin":-0.7,"dcOffsetMax":-0.3,
            "randomizeDepth":true,"depthMin":0.3,"depthMax":0.7}"""
        val dto = json.decodeFromString<ModulatorDto>(legacy)
        val mod = dto.toDomain()
        assertEquals(RollSpace.ENDS, mod.rollSpace)
        assertTrue(mod.randomizeLfoMin && mod.randomizeLfoMax)
        assertEquals(-0.7f, mod.lfoMinRollMin, eps); assertEquals(-0.3f, mod.lfoMinRollMax, eps)
        assertEquals(0.3f, mod.lfoMaxRollMin, eps); assertEquals(0.7f, mod.lfoMaxRollMax, eps)
        assertFalse(mod.randomizeDcOffset || mod.randomizeDepth)
        // The saved file and the migrated state compare equal, so a legacy preset does not load dirty.
        // (seqSteps is filled in on load, so compare without it.)
        val back = mod.toDto().copy(seqSteps = emptyList())
        assertEquals(dto, back)
        assertEquals(dto.hashCode(), back.hashCode())
    }

    @Test
    fun legacySeqAndMidiStayBaseAmount() {
        for (src in listOf("seq", "midi_cc_0_7")) {
            val legacy = """{"sourceId":"$src","operator":"ADD","depth":0.5,"dcOffset":0.1,
                "randomizeDepth":true,"depthMin":0.2,"depthMax":0.8}"""
            val mod = json.decodeFromString<ModulatorDto>(legacy).toDomain()
            assertEquals(RollSpace.BASE_AMOUNT, mod.rollSpace, src)
            assertTrue(mod.randomizeDepth)
            assertEquals(0.2f, mod.depthMin, eps); assertEquals(0.8f, mod.depthMax, eps)
        }
    }

    @Test
    fun newFilesRoundTrip() {
        val mod = CvModulator(sourceId = "lfo", depth = 0.4f, dcOffset = 0.1f, rollSpace = RollSpace.ENDS,
            randomizeLfoMax = true, lfoMaxRollMin = 0.3f, lfoMaxRollMax = 0.9f)
        val text = json.encodeToString(ModulatorDto.serializer(), mod.toDto())
        assertTrue(text.contains("\"rollSpace\":\"ENDS\""))
        val back = json.decodeFromString<ModulatorDto>(text).toDomain()
        assertEquals(mod.rollSpace, back.rollSpace)
        assertTrue(back.randomizeLfoMax); assertFalse(back.randomizeLfoMin)
        assertEquals(0.9f, back.lfoMaxRollMax, eps)
    }
}
