package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import io.mockk.mockk
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParametersValMuteTest {

    private lateinit var mixer: Mixer
    private lateinit var session: SessionContext

    @BeforeTest
    fun setup() {
        ImGui.createContext()
        ImGui.getIO().fonts.build()
        mixer = mockk(relaxed = true)
        session = SessionContext()
    }

    @AfterTest
    fun teardown() {
        try {
            ImGui.destroyContext()
        } catch (ignored: Throwable) {}
    }

    private fun simulateFrameWithMouse(mouseX: Float, mouseY: Float, mouseButton: Int, block: () -> Unit) {
        val io = ImGui.getIO()
        io.setDisplaySize(800f, 600f)
        io.setDeltaTime(1f / 60f)
        val flags = ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoScrollbar

        // Frame 1: Hover over target position
        io.addMousePosEvent(mouseX, mouseY)
        io.addMouseButtonEvent(mouseButton, false)
        ImGui.newFrame()
        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(800f, 600f)
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0f, 0f)
        if (ImGui.begin("TestWindow", flags)) {
            block()
            ImGui.dummy(800f, 600f)
            ImGui.end()
        }
        ImGui.popStyleVar()
        ImGui.render()

        // Frame 2: Mouse click down
        io.addMousePosEvent(mouseX, mouseY)
        io.addMouseButtonEvent(mouseButton, true)
        ImGui.newFrame()
        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(800f, 600f)
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0f, 0f)
        try {
            if (ImGui.begin("TestWindow", flags)) {
                block()
                ImGui.dummy(800f, 600f)
                ImGui.end()
            }
            ImGui.popStyleVar()
        } finally {
            ImGui.render()
            io.addMouseButtonEvent(mouseButton, false)
            ImGui.newFrame()
            ImGui.setNextWindowPos(0f, 0f)
            ImGui.setNextWindowSize(800f, 600f)
            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0f, 0f)
            if (ImGui.begin("TestWindow", flags)) {
                block()
                ImGui.dummy(800f, 600f)
                ImGui.end()
            }
            ImGui.popStyleVar()
            ImGui.render()
        }
    }

    @Test
    fun testRightClickValMutesAllModulators() {
        val param = ModulatableParameter(baseValue = 1.0f, minClamp = 0f, maxClamp = 10f)
        val mod1 = CvModulator(sourceId = "lfo", depth = 0.5f, bypassed = false)
        val mod2 = CvModulator(sourceId = "audio_bass", depth = 0.8f, bypassed = false)
        param.modulators.add(mod1)
        param.modulators.add(mod2)

        val state = ParametersState()
        val metrics = GridMetrics.compute(session)
        val cell = metrics.cell
        val labelColW = 100f
        val valColX = labelColW + 0f // getColumnOffset("value") is 0f
        val mouseX = valColX + cell * 0.5f
        val mouseY = cell * 0.5f

        // Right click (mouseButton = 1)
        simulateFrameWithMouse(mouseX, mouseY, 1) {
            ParametersRenderer.drawParamRow(
                session = session,
                label = "Lobes",
                paramKey = "Deck A/Mandala/Lobes",
                param = param,
                state = state,
                labelColW = labelColW,
                mixer = mixer,
                gridStartX = 0f,
                rowIndex = 0,
                getCvColumns = { emptyList() },
                getColumnOffset = { 0f },
                getCvColor = { _, _ -> 0 },
                onPushUndo = {}
            )
        }

        // Modulators should NOT be cleared, but both should be bypassed (muted)
        assertEquals(2, param.modulators.size)
        assertTrue(param.modulators[0].bypassed)
        assertTrue(param.modulators[1].bypassed)
    }

    @Test
    fun testRightClickValUnmutesAllModulatorsWhenMuted() {
        val param = ModulatableParameter(baseValue = 1.0f, minClamp = 0f, maxClamp = 10f)
        val mod1 = CvModulator(sourceId = "lfo", depth = 0.5f, bypassed = true)
        val mod2 = CvModulator(sourceId = "audio_bass", depth = 0.8f, bypassed = true)
        param.modulators.add(mod1)
        param.modulators.add(mod2)

        val state = ParametersState()
        val metrics = GridMetrics.compute(session)
        val cell = metrics.cell
        val labelColW = 100f
        val valColX = labelColW + 0f
        val mouseX = valColX + cell * 0.5f
        val mouseY = cell * 0.5f

        // Right click (mouseButton = 1)
        simulateFrameWithMouse(mouseX, mouseY, 1) {
            ParametersRenderer.drawParamRow(
                session = session,
                label = "Lobes",
                paramKey = "Deck A/Mandala/Lobes",
                param = param,
                state = state,
                labelColW = labelColW,
                mixer = mixer,
                gridStartX = 0f,
                rowIndex = 0,
                getCvColumns = { emptyList() },
                getColumnOffset = { 0f },
                getCvColor = { _, _ -> 0 },
                onPushUndo = {}
            )
        }

        // Both modulators should now be unmuted
        assertEquals(2, param.modulators.size)
        assertFalse(param.modulators[0].bypassed)
        assertFalse(param.modulators[1].bypassed)
    }

    @Test
    fun testMiddleClickValWithModulatorsTogglesMuteInsteadOfClearing() {
        val param = ModulatableParameter(baseValue = 1.0f, minClamp = 0f, maxClamp = 10f)
        val mod1 = CvModulator(sourceId = "lfo", depth = 0.5f, bypassed = false)
        param.modulators.add(mod1)

        val state = ParametersState()
        val metrics = GridMetrics.compute(session)
        val cell = metrics.cell
        val labelColW = 100f
        val valColX = labelColW + 0f
        val mouseX = valColX + cell * 0.5f
        val mouseY = cell * 0.5f

        // Middle click (mouseButton = 2)
        simulateFrameWithMouse(mouseX, mouseY, 2) {
            ParametersRenderer.drawParamRow(
                session = session,
                label = "Lobes",
                paramKey = "Deck A/Mandala/Lobes",
                param = param,
                state = state,
                labelColW = labelColW,
                mixer = mixer,
                gridStartX = 0f,
                rowIndex = 0,
                getCvColumns = { emptyList() },
                getColumnOffset = { 0f },
                getCvColor = { _, _ -> 0 },
                onPushUndo = {}
            )
        }

        // Modulators should NOT be cleared, but bypassed
        assertEquals(1, param.modulators.size)
        assertTrue(param.modulators[0].bypassed)
    }

    @Test
    fun testMiddleClickValWithoutModulatorsResetsBaseValue() {
        val param = ModulatableParameter(baseValue = 1.0f, minClamp = 0f, maxClamp = 10f)
        param.baseValue = 5.0f // modified from defaultValue (1.0f)

        val state = ParametersState()
        val metrics = GridMetrics.compute(session)
        val cell = metrics.cell
        val labelColW = 100f
        val valColX = labelColW + 0f
        val mouseX = valColX + cell * 0.5f
        val mouseY = cell * 0.5f

        // Middle click (mouseButton = 2)
        simulateFrameWithMouse(mouseX, mouseY, 2) {
            ParametersRenderer.drawParamRow(
                session = session,
                label = "Lobes",
                paramKey = "Deck A/Mandala/Lobes",
                param = param,
                state = state,
                labelColW = labelColW,
                mixer = mixer,
                gridStartX = 0f,
                rowIndex = 0,
                getCvColumns = { emptyList() },
                getColumnOffset = { 0f },
                getCvColor = { _, _ -> 0 },
                onPushUndo = {}
            )
        }

        // Base value should reset to defaultValue
        assertEquals(1.0f, param.baseValue)
    }
}
