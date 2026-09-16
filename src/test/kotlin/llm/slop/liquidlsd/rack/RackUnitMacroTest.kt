package llm.slop.liquidlsd.rack

import io.mockk.mockk
import llm.slop.liquidlsd.macro.*
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Mixer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RackUnitMacroTest {

    private lateinit var rackManager: RackManager
    private val dummyMixer = mockk<Mixer>(relaxed = true)

    @BeforeEach
    fun setUp() {
        rackManager = RackManager(allocateGlBuffers = false)
        MacroEngine.registerBank(null, MacroBank())
    }

    @AfterEach
    fun tearDown() {
        rackManager.dispose()
        MacroEngine.registerBank(null, MacroBank())
        MacroLearnState.cancelLearn()
    }

    @Test
    fun testUnitRegistersAndUnregistersBankWithMacroEngine() {
        val unit = GenericRackUnit(
            id = "test_unit_reg",
            label = "Register Test",
            unitType = RackUnitType.PROCESSOR
        )

        assertNull(MacroEngine.getBank("test_unit_reg"))

        rackManager.addUnit(unit)
        assertSame(unit.macroBank, MacroEngine.getBank("test_unit_reg"))

        rackManager.removeUnit("test_unit_reg")
        assertNull(MacroEngine.getBank("test_unit_reg"))
    }

    @Test
    fun testTwoUnitsWithIdenticalParameterNamesEvaluateIndependently() {
        // Unit 1
        val unit1Param = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 10f)
        val unit1 = GenericRackUnit(
            id = "unit_alpha",
            label = "Unit Alpha",
            unitType = RackUnitType.PROCESSOR,
            namedParams = mapOf("zoom" to unit1Param)
        )

        // Unit 2
        val unit2Param = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 10f)
        val unit2 = GenericRackUnit(
            id = "unit_beta",
            label = "Unit Beta",
            unitType = RackUnitType.PROCESSOR,
            namedParams = mapOf("zoom" to unit2Param)
        )

        rackManager.addUnit(unit1)
        rackManager.addUnit(unit2)

        // Bind Macro Knob 0 on unit1 to "zoom"
        val knob1 = unit1.macroBank.knobs[0]
        knob1.value = 0.5f
        knob1.bindings.add(
            MacroBinding(
                unitInstanceId = "unit_alpha",
                parameterId = "zoom",
                targetType = MacroTargetType.PARAM_BASE_VALUE,
                minVal = 0f,
                maxVal = 10f,
                curve = MacroCurveType.LINEAR
            )
        )

        // Bind Macro Knob 0 on unit2 to "zoom"
        val knob2 = unit2.macroBank.knobs[0]
        knob2.value = 0.9f
        knob2.bindings.add(
            MacroBinding(
                unitInstanceId = "unit_beta",
                parameterId = "zoom",
                targetType = MacroTargetType.PARAM_BASE_VALUE,
                minVal = 0f,
                maxVal = 10f,
                curve = MacroCurveType.LINEAR
            )
        )

        MacroEngine.invalidate()
        MacroEngine.tick(dummyMixer)

        // Verify independent evaluation without cross-unit collision
        assertEquals(5.0f, unit1Param.baseValue, 1e-4f, "Unit Alpha zoom should reflect Alpha's macro (0.5 * 10 = 5.0)")
        assertEquals(9.0f, unit2Param.baseValue, 1e-4f, "Unit Beta zoom should reflect Beta's macro (0.9 * 10 = 9.0)")

        // Change unit1 knob value and re-tick
        knob1.value = 0.2f
        MacroEngine.tick(dummyMixer)

        assertEquals(2.0f, unit1Param.baseValue, 1e-4f)
        assertEquals(9.0f, unit2Param.baseValue, 1e-4f, "Unit Beta zoom must remain untouched when Unit Alpha changes")
    }

    @Test
    fun testUnitMacroBindingCurveAndInversion() {
        val param = ModulatableParameter(0.0f, minClamp = 0f, maxClamp = 1f)
        val unit = GenericRackUnit(
            id = "unit_curve",
            label = "Curve Test Unit",
            unitType = RackUnitType.PROCESSOR,
            namedParams = mapOf("cutoff" to param)
        )
        rackManager.addUnit(unit)

        val knob = unit.macroBank.knobs[0]
        knob.value = 0.5f
        knob.bindings.add(
            MacroBinding(
                unitInstanceId = "unit_curve",
                parameterId = "cutoff",
                targetType = MacroTargetType.PARAM_BASE_VALUE,
                minVal = 0f,
                maxVal = 1f,
                curve = MacroCurveType.EXPONENTIAL,
                inverted = true
            )
        )

        MacroEngine.invalidate()
        MacroEngine.tick(dummyMixer)

        // shape(0.5, EXPONENTIAL) = 0.25. inverted => 1.0 - 0.25 = 0.75
        assertEquals(0.75f, param.baseValue, 1e-4f)

        // Set knob to 0.0 -> shape = 0.0 -> inverted gives 1.0
        knob.value = 0.0f
        MacroEngine.tick(dummyMixer)
        assertEquals(1.0f, param.baseValue, 1e-4f)

        // Set knob to 1.0 -> shape = 1.0 -> inverted gives 0.0
        knob.value = 1.0f
        MacroEngine.tick(dummyMixer)
        assertEquals(0.0f, param.baseValue, 1e-4f)
    }

    @Test
    fun testLearnStateAutoScopesToUnitBank() {
        val unit = GenericRackUnit(
            id = "unit_learn",
            label = "Learn Unit",
            unitType = RackUnitType.PROCESSOR
        )
        rackManager.addUnit(unit)

        val targetKnob = unit.macroBank.knobs[2]
        MacroLearnState.startLearn(targetKnob.id)
        assertTrue(MacroLearnState.isControlLearning(targetKnob.id))

        val bound = MacroLearnState.bindTarget(
            bank = unit.macroBank,
            targetType = MacroTargetType.PARAM_BASE_VALUE,
            parameterId = "mixRate",
            unitInstanceId = "unit_learn"
        )

        assertTrue(bound)
        assertEquals(1, targetKnob.bindings.size)
        val createdBinding = targetKnob.bindings[0]
        assertEquals("unit_learn", createdBinding.unitInstanceId)
        assertEquals("mixRate", createdBinding.parameterId)
        assertFalse(MacroLearnState.isControlLearning(targetKnob.id))
    }

    @Test
    fun testMacroCurationExpansionHeight() {
        val unit = GenericRackUnit(id = "height_test", label = "Height", unitType = RackUnitType.GENERATOR, heightU = 1)

        val normalH = llm.slop.liquidlsd.rack.ui.RackChassisRenderer.calculateUnitHeight(unit.heightU, isCollapsed = false, isMacroCurationOpen = false)
        assertEquals(llm.slop.liquidlsd.rack.ui.RackChassisRenderer.U_HEIGHT_PX, normalH)

        val curatedH = llm.slop.liquidlsd.rack.ui.RackChassisRenderer.calculateUnitHeight(unit.heightU, isCollapsed = false, isMacroCurationOpen = true)
        assertEquals(260.0f, curatedH)

        val collapsedH = llm.slop.liquidlsd.rack.ui.RackChassisRenderer.calculateUnitHeight(unit.heightU, isCollapsed = true, isMacroCurationOpen = true)
        assertEquals(llm.slop.liquidlsd.rack.ui.RackChassisRenderer.SPINE_HEIGHT_PX, collapsedH, "Collapsed state takes precedence over curation drawer height")
    }
}
