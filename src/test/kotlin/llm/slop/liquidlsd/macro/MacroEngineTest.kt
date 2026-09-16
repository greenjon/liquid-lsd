package llm.slop.liquidlsd.macro

import io.mockk.every
import io.mockk.mockk
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Uses the mockk-a-Mixer-and-stub-getParameterPaths pattern from
 * llm.slop.liquidlsd.parameters.ParameterResolverTest, since MacroEngine resolves bindings
 * through the same ParameterResolver.findParameterByPath(mixer, path) entry point that
 * MidiMappingManager uses.
 */
class MacroEngineTest {

    private val testUnitId = "macro-engine-test-unit"

    @BeforeTest
    fun setUp() {
        resetMacroEngine()
        ParameterResolver.clearCache()
    }

    @AfterTest
    fun tearDown() {
        resetMacroEngine()
        ParameterResolver.clearCache()
    }

    private fun resetMacroEngine() {
        MacroEngine.registerBank(null, MacroBank())
        MacroEngine.unregisterBank(testUnitId)
    }

    private fun createTestMixer(paths: List<Pair<String, ModulatableParameter>>): Mixer {
        val mixer = mockk<Mixer>()
        every { mixer.getParameterPaths("Mixer") } returns paths
        return mixer
    }

    // --- PARAM_BASE_VALUE ---

    @Test
    fun testParamBaseValueBindingSetsBaseValueOnTick() {
        val zoom = ModulatableParameter(0.0f, minClamp = -1f, maxClamp = 1f)
        val mixer = createTestMixer(listOf("Deck A/fbZoom" to zoom))

        val binding = MacroBinding(
            parameterId = "Deck A/fbZoom",
            targetType = MacroTargetType.PARAM_BASE_VALUE,
            minVal = -1f,
            maxVal = 1f,
            curve = MacroCurveType.LINEAR
        )
        val bank = MacroBank(knobs = listOf(MacroControl(label = "K1", value = 0.75f, bindings = mutableListOf(binding))))
        MacroEngine.registerBank(null, bank)

        MacroEngine.tick(mixer)

        // LINEAR, range [-1,1], macroVal=0.75 -> -1 + 0.75*2 = 0.5
        assertEquals(0.5f, zoom.baseValue, absoluteTolerance = 1e-5f)
    }

    @Test
    fun testParamBaseValueBindingUpdatesEachTickAsControlValueChanges() {
        val zoom = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
        val mixer = createTestMixer(listOf("Deck A/fbZoom" to zoom))

        val binding = MacroBinding(parameterId = "Deck A/fbZoom", targetType = MacroTargetType.PARAM_BASE_VALUE)
        val control = MacroControl(label = "K1", value = 0.2f, bindings = mutableListOf(binding))
        MacroEngine.registerBank(null, MacroBank(knobs = listOf(control)))

        MacroEngine.tick(mixer)
        assertEquals(0.2f, zoom.baseValue, absoluteTolerance = 1e-5f)

        control.value = 0.9f
        MacroEngine.tick(mixer)
        assertEquals(0.9f, zoom.baseValue, absoluteTolerance = 1e-5f)
    }

    // --- MODULATOR_PROPERTY ---

    @Test
    fun testModulatorPropertyBindingMutatesOnlyTargetField() {
        val param = ModulatableParameter(0.0f)
        val mod = CvModulator(sourceId = "lfo", subdivision = 1.0f, morph = 0.3f, depth = 0.5f)
        param.modulators.add(mod)
        val mixer = createTestMixer(listOf("Deck A/warp" to param))

        val binding = MacroBinding(
            parameterId = "Deck A/warp",
            targetType = MacroTargetType.MODULATOR_PROPERTY,
            modulatorIndex = 0,
            propertyName = "subdivision",
            minVal = 0.1f,
            maxVal = 10.0f
        )
        val control = MacroControl(label = "K1", value = 1.0f, bindings = mutableListOf(binding))
        MacroEngine.registerBank(null, MacroBank(knobs = listOf(control)))

        MacroEngine.tick(mixer)

        val updated = param.modulators[0]
        assertEquals(10.0f, updated.subdivision, absoluteTolerance = 1e-5f)
        // Unrelated fields must be untouched.
        assertEquals(0.3f, updated.morph, absoluteTolerance = 1e-5f)
        assertEquals(0.5f, updated.depth, absoluteTolerance = 1e-5f)
    }

    @Test
    fun testUnknownPropertyNameDoesNotCrashOrMutate() {
        val param = ModulatableParameter(0.0f)
        val mod = CvModulator(sourceId = "lfo", subdivision = 1.0f, morph = 0.3f)
        param.modulators.add(mod)
        val mixer = createTestMixer(listOf("Deck A/warp" to param))

        val binding = MacroBinding(
            parameterId = "Deck A/warp",
            targetType = MacroTargetType.MODULATOR_PROPERTY,
            modulatorIndex = 0,
            propertyName = "notARealProperty",
            minVal = 0f,
            maxVal = 1f
        )
        val control = MacroControl(label = "K1", value = 1.0f, bindings = mutableListOf(binding))
        MacroEngine.registerBank(null, MacroBank(knobs = listOf(control)))

        // Should not throw.
        MacroEngine.tick(mixer)

        val unchanged = param.modulators[0]
        assertEquals(1.0f, unchanged.subdivision, absoluteTolerance = 1e-5f)
        assertEquals(0.3f, unchanged.morph, absoluteTolerance = 1e-5f)
    }

    // --- enabled = false ---

    @Test
    fun testDisabledBindingIsNotAppliedOnTick() {
        val zoom = ModulatableParameter(0.42f, minClamp = -1f, maxClamp = 1f)
        val mixer = createTestMixer(listOf("Deck A/fbZoom" to zoom))

        val binding = MacroBinding(
            parameterId = "Deck A/fbZoom",
            targetType = MacroTargetType.PARAM_BASE_VALUE,
            enabled = false
        )
        val control = MacroControl(label = "K1", value = 1.0f, bindings = mutableListOf(binding))
        MacroEngine.registerBank(null, MacroBank(knobs = listOf(control)))

        MacroEngine.tick(mixer)

        assertEquals(0.42f, zoom.baseValue, "Disabled binding must not mutate its target")
    }

    @Test
    fun testDisabledBindingExcludedFromFindBindingsTargeting() {
        val zoom = ModulatableParameter(0.0f)
        val mixer = createTestMixer(listOf("Deck A/fbZoom" to zoom))

        val enabledBinding = MacroBinding(parameterId = "Deck A/fbZoom", targetType = MacroTargetType.PARAM_BASE_VALUE, enabled = true)
        val disabledBinding = MacroBinding(parameterId = "Deck A/otherParam", targetType = MacroTargetType.PARAM_BASE_VALUE, enabled = false)
        // Only the enabled one resolves against a real parameter, but even if it did resolve,
        // findBindingsTargeting must not surface disabled bindings.
        val control = MacroControl(label = "K1", bindings = mutableListOf(enabledBinding, disabledBinding))
        MacroEngine.registerBank(null, MacroBank(knobs = listOf(control)))

        MacroEngine.tick(mixer)

        val foundEnabled = MacroEngine.findBindingsTargeting(null, "Deck A/fbZoom")
        assertEquals(1, foundEnabled.size)
        assertTrue(foundEnabled.contains(enabledBinding))

        val foundDisabled = MacroEngine.findBindingsTargeting(null, "Deck A/otherParam")
        assertTrue(foundDisabled.isEmpty(), "Disabled bindings must never appear in findBindingsTargeting results")
    }

    // --- Multiple bindings per control ---

    @Test
    fun testMultipleBindingsOnOneControlUpdateIndependently() {
        val paramA = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
        val paramB = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
        val mod = CvModulator(sourceId = "lfo")
        val paramC = ModulatableParameter(0.0f).apply { modulators.add(mod) }
        val paramD = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)

        val mixer = createTestMixer(
            listOf(
                "A" to paramA,
                "B" to paramB,
                "C" to paramC,
                "D" to paramD
            )
        )

        val bindingLinear = MacroBinding(parameterId = "A", targetType = MacroTargetType.PARAM_BASE_VALUE, minVal = 0f, maxVal = 1f, curve = MacroCurveType.LINEAR)
        val bindingInverted = MacroBinding(parameterId = "B", targetType = MacroTargetType.PARAM_BASE_VALUE, minVal = 0f, maxVal = 1f, curve = MacroCurveType.LINEAR, inverted = true)
        val bindingModProp = MacroBinding(parameterId = "C", targetType = MacroTargetType.MODULATOR_PROPERTY, modulatorIndex = 0, propertyName = "depth", minVal = 0f, maxVal = 2f)
        val bindingStep = MacroBinding(parameterId = "D", targetType = MacroTargetType.PARAM_BASE_VALUE, minVal = 0f, maxVal = 1f, curve = MacroCurveType.STEP, stepCount = 4)

        assertTrue(4 <= MacroControl.MAX_BINDINGS_PER_CONTROL)
        val control = MacroControl(
            label = "K1",
            value = 0.6f,
            bindings = mutableListOf(bindingLinear, bindingInverted, bindingModProp, bindingStep)
        )
        MacroEngine.registerBank(null, MacroBank(knobs = listOf(control)))

        MacroEngine.tick(mixer)

        assertEquals(0.6f, paramA.baseValue, absoluteTolerance = 1e-5f)
        assertEquals(0.4f, paramB.baseValue, absoluteTolerance = 1e-5f)
        assertEquals(1.2f, paramC.modulators[0].depth, absoluteTolerance = 1e-5f)
        // STEP with stepCount=4, v=0.6 -> floor(0.6*4)=2 -> 2/3
        assertEquals(2f / 3f, paramD.baseValue, absoluteTolerance = 1e-5f)
    }

    // --- Dirty / rebuild semantics ---

    @Test
    fun testMutatingBindingsOnAlreadyRegisteredBankRequiresExplicitInvalidate() {
        val param = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
        val mixer = createTestMixer(listOf("Deck A/fbZoom" to param))

        val control = MacroControl(label = "K1", value = 1.0f)
        MacroEngine.registerBank(null, MacroBank(knobs = listOf(control)))
        MacroEngine.tick(mixer) // rebuild happens here with no bindings yet

        // Mutate the already-registered bank's control in place, without calling invalidate().
        control.bindings.add(MacroBinding(parameterId = "Deck A/fbZoom", targetType = MacroTargetType.PARAM_BASE_VALUE))

        MacroEngine.tick(mixer)
        assertEquals(0.0f, param.baseValue, "Known Phase 1 limitation: adding a binding in place does not take effect until invalidate() is called")

        MacroEngine.invalidate()
        MacroEngine.tick(mixer)
        assertEquals(1.0f, param.baseValue, "After invalidate(), the new binding should be resolved and applied")
    }

    @Test
    fun testRegisterBankInvalidatesCacheImmediately() {
        val param = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
        val mixer = createTestMixer(listOf("Deck A/fbZoom" to param))

        MacroEngine.registerBank(null, MacroBank())
        MacroEngine.tick(mixer)

        val binding = MacroBinding(parameterId = "Deck A/fbZoom", targetType = MacroTargetType.PARAM_BASE_VALUE)
        val control = MacroControl(label = "K1", value = 1.0f, bindings = mutableListOf(binding))
        // Re-registering the bank (rather than mutating in place) must invalidate automatically.
        MacroEngine.registerBank(null, MacroBank(knobs = listOf(control)))

        MacroEngine.tick(mixer)
        assertEquals(1.0f, param.baseValue)
    }

    // --- findBindingsTargeting exact vs filtered lookups ---

    @Test
    fun testFindBindingsTargetingExactAndFilteredLookups() {
        val param = ModulatableParameter(0.0f)
        val mod0 = CvModulator(sourceId = "lfo")
        val mod1 = CvModulator(sourceId = "lfo")
        param.modulators.add(mod0)
        param.modulators.add(mod1)
        val mixer = createTestMixer(listOf("Deck A/warp" to param))

        val bSubdiv0 = MacroBinding(parameterId = "Deck A/warp", targetType = MacroTargetType.MODULATOR_PROPERTY, modulatorIndex = 0, propertyName = "subdivision")
        val bMorph0 = MacroBinding(parameterId = "Deck A/warp", targetType = MacroTargetType.MODULATOR_PROPERTY, modulatorIndex = 0, propertyName = "morph")
        val bSubdiv1 = MacroBinding(parameterId = "Deck A/warp", targetType = MacroTargetType.MODULATOR_PROPERTY, modulatorIndex = 1, propertyName = "subdivision")

        val control = MacroControl(label = "K1", bindings = mutableListOf(bSubdiv0, bMorph0, bSubdiv1))
        MacroEngine.registerBank(null, MacroBank(knobs = listOf(control)))
        MacroEngine.tick(mixer)

        val allForParam = MacroEngine.findBindingsTargeting(null, "Deck A/warp")
        assertEquals(3, allForParam.size)

        val onlyMod0 = MacroEngine.findBindingsTargeting(null, "Deck A/warp", modulatorIndex = 0)
        assertEquals(2, onlyMod0.size)
        assertTrue(onlyMod0.containsAll(listOf(bSubdiv0, bMorph0)))

        val onlySubdivision = MacroEngine.findBindingsTargeting(null, "Deck A/warp", propertyName = "subdivision")
        assertEquals(2, onlySubdivision.size)
        assertTrue(onlySubdivision.containsAll(listOf(bSubdiv0, bSubdiv1)))

        val exact = MacroEngine.findBindingsTargeting(null, "Deck A/warp", modulatorIndex = 1, propertyName = "subdivision")
        assertEquals(1, exact.size)
        assertTrue(exact.contains(bSubdiv1))

        val wrongUnit = MacroEngine.findBindingsTargeting(testUnitId, "Deck A/warp")
        assertTrue(wrongUnit.isEmpty(), "Bindings scoped to the global bank must not match a different unitInstanceId filter")

        val wrongParam = MacroEngine.findBindingsTargeting(null, "Deck A/doesNotExist")
        assertTrue(wrongParam.isEmpty())
    }

    @Test
    fun testUnitScopedBankResolvesIndependentlyOfGlobalBank() {
        val globalParam = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
        val unitParam = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
        val mixer = createTestMixer(listOf("Deck A/fbZoom" to globalParam, "dimensionWarp" to unitParam))

        val globalBinding = MacroBinding(parameterId = "Deck A/fbZoom", targetType = MacroTargetType.PARAM_BASE_VALUE)
        MacroEngine.registerBank(null, MacroBank(knobs = listOf(MacroControl(value = 0.3f, bindings = mutableListOf(globalBinding)))))

        val unitBinding = MacroBinding(unitInstanceId = testUnitId, parameterId = "dimensionWarp", targetType = MacroTargetType.PARAM_BASE_VALUE)
        MacroEngine.registerBank(testUnitId, MacroBank(knobs = listOf(MacroControl(value = 0.8f, bindings = mutableListOf(unitBinding)))))

        MacroEngine.tick(mixer)

        assertEquals(0.3f, globalParam.baseValue, absoluteTolerance = 1e-5f)
        assertEquals(0.8f, unitParam.baseValue, absoluteTolerance = 1e-5f)

        assertTrue(MacroEngine.findBindingsTargeting(null, "dimensionWarp").isEmpty())
        assertEquals(1, MacroEngine.findBindingsTargeting(testUnitId, "dimensionWarp").size)
    }

    @Test
    fun testUnresolvableParameterPathIsSkippedWithoutCrashing() {
        val param = ModulatableParameter(0.0f)
        val mixer = createTestMixer(listOf("Deck A/warp" to param))

        val badBinding = MacroBinding(parameterId = "Deck A/doesNotExist", targetType = MacroTargetType.PARAM_BASE_VALUE)
        val control = MacroControl(value = 1.0f, bindings = mutableListOf(badBinding))
        MacroEngine.registerBank(null, MacroBank(knobs = listOf(control)))

        // Should not throw.
        MacroEngine.tick(mixer)
        assertFalse(MacroEngine.findBindingsTargeting(null, "Deck A/doesNotExist").isNotEmpty())
    }

    @Test
    fun testUnresolvableModulatorIndexIsSkippedWithoutCrashing() {
        val param = ModulatableParameter(0.0f)
        // No modulators added, so index 0 does not resolve.
        val mixer = createTestMixer(listOf("Deck A/warp" to param))

        val badBinding = MacroBinding(parameterId = "Deck A/warp", targetType = MacroTargetType.MODULATOR_PROPERTY, modulatorIndex = 0, propertyName = "subdivision")
        val control = MacroControl(value = 1.0f, bindings = mutableListOf(badBinding))
        MacroEngine.registerBank(null, MacroBank(knobs = listOf(control)))

        // Should not throw, and should not appear in the resolved cache.
        MacroEngine.tick(mixer)
        assertTrue(MacroEngine.findBindingsTargeting(null, "Deck A/warp").isEmpty())
    }

    private fun assertEquals(expected: Float, actual: Float, absoluteTolerance: Float, message: String? = null) {
        assertTrue(kotlin.math.abs(expected - actual) <= absoluteTolerance, message ?: "Expected $expected but was $actual (tolerance $absoluteTolerance)")
    }
}
