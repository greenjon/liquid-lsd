package llm.slop.liquidlsd.parameters

import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.cv.evaluateModulatorAtOffset
import llm.slop.liquidlsd.cv.getCombinedEffectiveValueAtOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScopeTimebaseTest {

    @Test
    fun `test formatTimeOffset correctly handles sub-second, seconds, minutes and hours`() {
        assertEquals("NOW", ScopeTimebase.formatTimeOffset(0.0f))
        assertEquals("-250ms", ScopeTimebase.formatTimeOffset(-0.25f))
        assertEquals("+500ms", ScopeTimebase.formatTimeOffset(0.5f))
        assertEquals("-2s", ScopeTimebase.formatTimeOffset(-2.0f))
        assertEquals("+10s", ScopeTimebase.formatTimeOffset(10.0f))
        assertEquals("-3m", ScopeTimebase.formatTimeOffset(-180.0f))
        assertEquals("+15m", ScopeTimebase.formatTimeOffset(900.0f))
        assertEquals("-2.5h", ScopeTimebase.formatTimeOffset(-9000.0f))
        assertEquals("+24h", ScopeTimebase.formatTimeOffset(86400.0f))
    }

    @Test
    fun `test ModulatableParameter resolveEffectiveTimebase auto derives from active LFO`() {
        val param = ModulatableParameter(baseValue = 0.5f)
        assertEquals(ScopeTimebase.AUTO, param.scopeTimebase)

        // No modulators -> defaults to 10s
        val (dur10, div10) = param.resolveEffectiveTimebase()
        assertEquals(10.0f, dur10)
        assertEquals(2.0f, div10)

        val (durOne, divOne) = param.resolveEffectiveTimebase(defaultWhenNoLfo = ScopeTimebase.ONE_SEC)
        assertEquals(1.0f, durOne)
        assertEquals(0.25f, divOne)

        // Add 24-hour LFO
        val lfo24h = CvModulator(
            id = "lfo_24h",
            sourceId = "lfo",
            genUnit = GenUnit.TIME,
            subdivision = 86400.0f
        )
        param.modulators.add(lfo24h)
        val (dur24, _) = param.resolveEffectiveTimebase()
        assertEquals(86400.0f, dur24)

        // Add fast 0.5s LFO
        param.modulators.clear()
        val fastLfo = CvModulator(
            id = "lfo_fast",
            sourceId = "lfo",
            genUnit = GenUnit.TIME,
            subdivision = 0.5f
        )
        param.modulators.add(fastLfo)
        val (durFast, _) = param.resolveEffectiveTimebase()
        assertEquals(1.0f, durFast)

        // Explicit timebase override
        param.scopeTimebase = ScopeTimebase.FIFTEEN_MIN
        val (durExplicit, divExplicit) = param.resolveEffectiveTimebase()
        assertEquals(900.0f, durExplicit)
        assertEquals(180.0f, divExplicit)
    }

    @Test
    fun `test decoupled per-scope timebases across LFO, Audio, and Final tabs`() {
        val param = ModulatableParameter(baseValue = 0.5f)
        param.setScopeTimebase("lfo", ScopeTimebase.TEN_SEC)
        param.setScopeTimebase("audio", ScopeTimebase.HUNDRED_SEC)
        param.setScopeTimebase("final", ScopeTimebase.FIFTEEN_MIN)

        assertEquals(ScopeTimebase.TEN_SEC, param.getScopeTimebase("lfo"))
        assertEquals(ScopeTimebase.HUNDRED_SEC, param.getScopeTimebase("audio"))
        assertEquals(ScopeTimebase.FIFTEEN_MIN, param.getScopeTimebase("final"))
        assertEquals(ScopeTimebase.TEN_SEC, param.getScopeTimebase("trigger"))

        assertEquals(10.0f, param.resolveEffectiveTimebase("lfo").first)
        assertEquals(100.0f, param.resolveEffectiveTimebase("audio").first)
        assertEquals(900.0f, param.resolveEffectiveTimebase("final").first)
        assertEquals(10.0f, param.resolveEffectiveTimebase("trigger").first)
    }

    @Test
    fun `test evaluateModulatorAtOffset future lookahead calculation`() {
        val mod = CvModulator(
            id = "test_lfo",
            sourceId = "lfo",
            genUnit = GenUnit.TIME,
            subdivision = 4.0f, // 4-second sine period
            waveform = Waveform.SINE
        )

        // Evaluate at now vs quarter cycle ahead (+1.0s)
        val valNow = evaluateModulatorAtOffset(mod, 0.0)
        val valQuarter = evaluateModulatorAtOffset(mod, 1.0)
        val valHalf = evaluateModulatorAtOffset(mod, 2.0)

        // Future evaluation produces valid numerical output in [-1, 1]
        assertTrue(valNow in -1.0f..1.0f)
        assertTrue(valQuarter in -1.0f..1.0f)
        assertTrue(valHalf in -1.0f..1.0f)

        // Combined effective value at offset
        val combined = getCombinedEffectiveValueAtOffset(listOf(mod), isBipolar = false, timeOffsetSec = 1.0)
        assertTrue(combined in 0.0f..1.0f)
    }

    @Test
    fun `test ModulatableParameter clone preserves customized scopeTimebases`() {
        val param = ModulatableParameter(baseValue = 0.75f)
        param.setScopeTimebase("lfo", ScopeTimebase.HUNDRED_SEC)
        param.setScopeTimebase("audio", ScopeTimebase.FIFTEEN_MIN)
        param.setScopeTimebase("final", ScopeTimebase.TWO_POINT_FIVE_HOURS)

        val cloned = param.clone()
        assertEquals(ScopeTimebase.HUNDRED_SEC, cloned.getScopeTimebase("lfo"))
        assertEquals(ScopeTimebase.FIFTEEN_MIN, cloned.getScopeTimebase("audio"))
        assertEquals(ScopeTimebase.TWO_POINT_FIVE_HOURS, cloned.getScopeTimebase("final"))
        assertEquals(ScopeTimebase.TEN_SEC, cloned.getScopeTimebase("trigger"))
    }

    @Test
    fun `test source classification helpers isAudioSource and isTriggerSource`() {
        assertTrue(llm.slop.liquidlsd.cv.isAudioSource("audio_amp"))
        assertTrue(llm.slop.liquidlsd.cv.isAudioSource("audio_bass"))
        assertTrue(llm.slop.liquidlsd.cv.isAudioSource("audio_mid"))
        assertTrue(llm.slop.liquidlsd.cv.isAudioSource("audio_high"))
        assertTrue(!llm.slop.liquidlsd.cv.isAudioSource("lfo"))
        assertTrue(!llm.slop.liquidlsd.cv.isAudioSource("trigger_onset"))

        assertTrue(llm.slop.liquidlsd.cv.isTriggerSource("trigger_onset"))
        assertTrue(llm.slop.liquidlsd.cv.isTriggerSource("trigger_accent"))
        assertTrue(!llm.slop.liquidlsd.cv.isTriggerSource("audio_amp"))
        assertTrue(!llm.slop.liquidlsd.cv.isTriggerSource("lfo"))
    }

    @Test
    fun `test CvHistoryBuffer sampleWindow interpolation and bounds`() {
        val buffer = llm.slop.liquidlsd.cv.CvHistoryBuffer(10)
        for (i in 1..10) {
            buffer.add(i * 10f) // 10.0, 20.0, ..., 100.0 (NOW = 100.0)
        }

        // Fraction 1.0 is newest (NOW)
        assertEquals(100f, buffer.sampleWindow(10, 1.0f))
        // Fraction 0.0 is oldest (10.0)
        assertEquals(10f, buffer.sampleWindow(10, 0.0f))
        // Fraction 0.5 is midpoint (55.0)
        assertEquals(55f, buffer.sampleWindow(10, 0.5f))

        // Span smaller than buffer size (last 4 samples: 70, 80, 90, 100)
        assertEquals(70f, buffer.sampleWindow(4, 0.0f))
        assertEquals(100f, buffer.sampleWindow(4, 1.0f))
        assertEquals(85f, buffer.sampleWindow(4, 0.5f))
    }

    @Test
    fun `test CvHistoryBuffer copyTo copies most recent samples without lag when target is smaller`() {
        val buffer = llm.slop.liquidlsd.cv.CvHistoryBuffer(10)
        for (i in 1..10) {
            buffer.add(i.toFloat()) // 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 (NOW = 10)
        }

        // Full copy
        val fullTarget = FloatArray(10)
        buffer.copyTo(fullTarget)
        for (i in 0 until 10) {
            assertEquals((i + 1).toFloat(), fullTarget[i])
        }

        // Smaller target (size 4) -> must receive the most recent 4 samples: 7, 8, 9, 10 (NOT oldest 1, 2, 3, 4)
        val smallTarget = FloatArray(4)
        buffer.copyTo(smallTarget)
        assertEquals(7.0f, smallTarget[0])
        assertEquals(8.0f, smallTarget[1])
        assertEquals(9.0f, smallTarget[2])
        assertEquals(10.0f, smallTarget[3])
    }

    @Test
    fun `test BeatSine is zero-centered bipolar oscillating between -1 and 1`() {
        val beatSine = llm.slop.liquidlsd.cv.BeatSine()
        beatSine.update(0.0, 0.0)
        assertEquals(0.0f, beatSine.value, 0.001f) // sin(0) = 0

        beatSine.update(0.25, 0.0)
        assertEquals(1.0f, beatSine.value, 0.001f) // sin(pi/2) = 1

        beatSine.update(0.5, 0.0)
        assertEquals(0.0f, beatSine.value, 0.001f) // sin(pi) = 0

        beatSine.update(0.75, 0.0)
        assertEquals(-1.0f, beatSine.value, 0.001f) // sin(3pi/2) = -1
    }
}


