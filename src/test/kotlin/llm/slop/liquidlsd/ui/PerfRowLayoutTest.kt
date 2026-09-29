package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Performance row layout contract: positions come from [PerfRowGeometry] (never from a row's
 * mode), content from [PerfKnobResolver], and the deck row's reserved width from [DeckRowMetrics].
 */
class PerfRowLayoutTest {

    private val bodyLineH = 16f
    private val leftW = 420f
    private val rightW = 56f

    private fun geo(gridW: Float, rowH: Float) = PerfRowGeometry(gridW, rowH, bodyLineH, leftW, rightW)

    // -- Geometry --------------------------------------------------------------------------

    @Test
    fun stripFitsBothLabelsAndFxCells() {
        val g = geo(1280f, 74f)
        assertTrue(g.stripH >= bodyLineH)
        assertTrue(g.stripH >= FxSlotCell.HEIGHT)
        assertTrue(g.stripH >= FxParamCell.HEIGHT)
        assertEquals(PerfRowGeometry.stripHeight(bodyLineH), g.stripH)
    }

    @Test
    fun knobAndStripFitInsideTheRowAtEverySupportedSize() {
        for (gridW in listOf(1000f, 1280f, 1600f, 1920f)) {
            for (rowH in listOf(68f, 74f, 95f, 140f, 220f)) {
                val g = geo(gridW, rowH)
                val contentTop = PerfRowGeometry.BOX_MARGIN_Y + PerfRowGeometry.BOX_PAD
                val contentBottom = rowH - contentTop
                assertTrue(g.knobTop >= contentTop - 0.01f, "knob above content at $gridW x $rowH")
                assertTrue(g.knobTop + g.widgetH <= contentBottom + 0.01f, "strip below content at $gridW x $rowH")
                assertEquals(g.knobTop + g.diameter + PerfRowGeometry.STRIP_GAP, g.stripTop)
            }
        }
    }

    @Test
    fun sideButtonsStayInsideTheirOwnColumnAndClearTheLeftControls() {
        for (gridW in listOf(1000f, 1280f, 1920f)) {
            val g = geo(gridW, 74f)
            assertTrue(g.sideBtnX(0) >= PerfRowGeometry.ROW_INSET_X + leftW, "col 0 side buttons overlap left controls at $gridW")
            for (col in 0 until 4) {
                val colLeft = g.clusterLeft + col * g.colW
                assertTrue(g.sideBtnX(col) >= colLeft - 0.01f, "col $col side buttons reach col ${col - 1} at $gridW")
                assertTrue(g.knobX(col) + g.diameter <= colLeft + g.colW + 0.01f, "col $col knob leaves its column at $gridW")
            }
        }
    }

    @Test
    fun sideButtonStacksAreCenteredOnTheKnob() {
        val g = geo(1280f, 95f)
        val knobCenterY = g.knobTop + g.diameter / 2f
        val single = g.sideBtnY(0, 1)
        assertEquals(knobCenterY, single + PerfRowGeometry.SIDE_BTN / 2f, 0.01f)
        val top = g.sideBtnY(0, 2)
        val bottom = g.sideBtnY(1, 2)
        assertEquals(knobCenterY, (top + bottom + PerfRowGeometry.SIDE_BTN) / 2f, 0.01f)
    }

    // -- Deck row reserved width ------------------------------------------------------------

    @Test
    fun deckRow1WidthMatchesTheDrawnControls() {
        val ctrlH = 24f
        val comboW = 100f
        val nav = (ctrlH * 0.85f).coerceAtLeast(20f)
        val queueNav = nav + 2f + 38f + 2f + nav
        // pill, badge, combo, eject, queue nav, save, kebab -- each followed by a 4px gap
        // except the last. SRC row ends cleanly at the kebab with no trailing growth slack.
        // Note: dice is placed in the right wing above the BYPASS button.
        val expected = 28f + 4f + 74f + 4f + comboW + 4f + ctrlH + 4f + queueNav +
            4f + ctrlH + 4f + DeckRowMetrics.KEBAB_W
        assertEquals(expected, DeckRowMetrics.row1Width(ctrlH, comboW), 0.01f)
    }

    @Test
    fun fxRowHeaderWidthFillsMaxWAndLinesUpKebabWithSrcRow() {
        val ctrlH = 24f
        val gap = 3f
        val saveW = FxChainHeader.saveBtnW(ctrlH)
        val slotPillsW = 20f * llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT + gap * (llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT - 1)
        val maxW = 320f

        // Case 1: showArrows == true (Decks A, B, BG)
        val nameWWithArrows = FxChainHeader.calculateNameWidth(maxW, ctrlH, showArrows = true)
        val totalDrawnWWithArrows = FxChainHeader.ARROW_W + gap + nameWWithArrows + gap + FxChainHeader.ARROW_W + gap + slotPillsW + gap + saveW + gap + FxChainHeader.MORE_BTN_W
        assertEquals(maxW, totalDrawnWWithArrows, 0.01f)

        // Case 2: showArrows == false (Deck PV, Master FX)
        val nameWNoArrows = FxChainHeader.calculateNameWidth(maxW, ctrlH, showArrows = false)
        val totalDrawnWNoArrows = nameWNoArrows + gap + slotPillsW + gap + saveW + gap + FxChainHeader.MORE_BTN_W
        assertEquals(maxW, totalDrawnWNoArrows, 0.01f)

        // Furthermore, FxChainHeader's MORE_BTN_W matches DeckRowMetrics.KEBAB_W
        assertEquals(DeckRowMetrics.KEBAB_W, FxChainHeader.MORE_BTN_W)
    }

    @Test
    fun masterRowFxAndMixRowsMatchDeckFxRowLength() {
        val deckBadgeW = 42f
        val masterTabBadgeW = 42f
        for (ctrlH in listOf(21f, 24f)) {
            for (comboW in listOf(85f, 100f, 140f)) {
                val deckFxRowW = DeckRowMetrics.row1Width(ctrlH, comboW)
                val masterRowW = DeckRowMetrics.row1Width(ctrlH, comboW)

                // Master FX row length: mode pill + gap + FxChainHeader (maxW = masterRowW - modeBtnW - gap)
                val modeBtnW = DeckRowMetrics.MODE_PILL_W
                val gap = DeckRowMetrics.GAP
                val fxMaxW = masterRowW - modeBtnW - gap
                val nameW = FxChainHeader.calculateNameWidth(fxMaxW, ctrlH, showArrows = false)
                val slotPillsW = 20f * llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT + 3f * (llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT - 1)
                val saveW = FxChainHeader.saveBtnW(ctrlH)
                val masterFxDrawnW = modeBtnW + gap + nameW + 3f + slotPillsW + 3f + saveW + 3f + FxChainHeader.MORE_BTN_W

                // Master MIX row length: mode pill + gap + badge + gap + reset button (badgeW = width - resetBtnW - gap)
                val mixWidth = masterRowW - modeBtnW - gap
                val resetBtnW = 44f
                val badgeW = mixWidth - resetBtnW - gap
                val masterMixDrawnW = modeBtnW + gap + badgeW + gap + resetBtnW

                // Visual right edge alignment: badge width + gap (6f) + row width
                val deckVisualRightEdge = deckBadgeW + 6f + deckFxRowW
                val masterVisualRightEdge = masterTabBadgeW + 6f + masterFxDrawnW
                assertEquals(deckFxRowW, masterFxDrawnW, 0.01f, "Master FX row length should equal deck FX row length")
                assertEquals(deckVisualRightEdge, masterVisualRightEdge, 0.01f, "Master FX row right edge should align with deck FX row right edge")
                assertEquals(masterFxDrawnW, masterMixDrawnW, 0.01f, "Master MIX row length should equal Master FX row length")
            }
        }
    }

    @Test
    fun compactControlsNeverNeedMoreThanTheReservedWidth() {
        assertTrue(
            DeckRowMetrics.row1Width(21f, 120f) <= DeckRowMetrics.row1Width(PerformanceColors.CTRL_H, 120f)
        )
    }

    @Test
    fun everyDeckReservesTheWiderOfQueueNavAndPreviewBadge() {
        for (ctrlH in listOf(21f, 24f)) {
            assertTrue(DeckRowMetrics.queueNavW(ctrlH) >= DeckRowMetrics.PV_BADGE_W)
        }
    }

    // -- Content resolver -------------------------------------------------------------------

    private fun bank() = MacroBank().also { b -> b.knobs.forEachIndexed { i, k -> k.label = "M${i + 1}" } }

    @Test
    fun sourceRowsShowLabelsAndNoSideButtons() {
        val specs = PerfKnobResolver.resolve(bank(), 0, null)
        assertEquals(4, specs.size)
        assertEquals(listOf("M1", "M2", "M3", "M4"), specs.map { (it.under as UnderKnob.Label).text })
        assertTrue(specs.all { it.side == SideButtons.None && it.valueOverlay == null })
    }

    @Test
    fun emptyLabelsFallBackToKnobNumber() {
        val b = MacroBank().also { it.knobs[2].label = "" }
        val specs = PerfKnobResolver.resolve(b, 0, null)
        assertEquals(UnderKnob.Label("K3"), specs[2].under)
    }

    @Test
    fun fxGroupModeShowsSuperKnobLabelThenSlotCells() {
        val specs = PerfKnobResolver.resolve(bank(), 0, FxRowState(null, 0, emptyList()))
        assertEquals(UnderKnob.Label("M1"), specs[0].under)
        assertEquals(SideButtons.None, specs[0].side)
        for (col in 1..3) {
            assertEquals(UnderKnob.SlotCell(col - 1), specs[col].under)
            assertEquals(SideButtons.LinkAndBypass(col - 1), specs[col].side)
        }
    }

    @Test
    fun fxFocusModeShowsFocusedSlotThenPagedParams() {
        val params = (1..5).map { "p$it" to ModulatableParameter(baseValue = it.toFloat()) }
        val page0 = PerfKnobResolver.resolve(bank(), 0, FxRowState(1, 0, params))
        assertEquals(UnderKnob.SlotCell(1), page0[0].under)
        assertEquals(SideButtons.Bypass(1), page0[0].side)
        assertEquals(listOf("p1", "p2", "p3"), page0.drop(1).map { (it.under as UnderKnob.ParamCell).name })
        assertEquals("2", page0[2].valueOverlay)
        assertTrue(page0[3].side is SideButtons.Reset)

        // Page 2 has only p4, p5: the last knob is a blank param cell in the same strip.
        val page1 = PerfKnobResolver.resolve(bank(), 0, FxRowState(1, 1, params))
        assertEquals(listOf("p4", "p5", null), page1.drop(1).map { (it.under as UnderKnob.ParamCell).name })
        assertEquals(SideButtons.None, page1[3].side)
        assertEquals(null, page1[3].valueOverlay)
    }

    @Test
    fun knobOffsetSelectsTheBankSlice() {
        val specs = PerfKnobResolver.resolve(bank(), 4, null)
        assertEquals(listOf("M5", "M6", "M7", "M8"), specs.map { (it.under as UnderKnob.Label).text })
        assertEquals(listOf(4, 5, 6, 7), specs.map { it.knobIndex })
    }

    @Test
    fun valuesFormatShortForTheKnobFace() {
        assertEquals("3", PerfKnobResolver.formatValue(3f))
        assertEquals("0.25", PerfKnobResolver.formatValue(0.25f))
        assertEquals("1500.00", PerfKnobResolver.formatValue(1500f))
    }

    // -- Mode accessor ----------------------------------------------------------------------

    @Test
    fun deckRowIsFxWhenEitherThePillOrTheDeepEditSubTabSaysSo() {
        for (pill in listOf(null, "SRC", "FX")) {
            for (subTab in listOf("SRC", "FX")) {
                val ctx = PerformanceUiContext()
                pill?.let { ctx.deckRowMode["B"] = it }
                val state = ParametersState().apply { activeDeckBSubTab = subTab }
                assertEquals(pill == "FX" || subTab == "FX", ctx.isDeckRowFx("B", state), "pill=$pill subTab=$subTab")
            }
        }
        assertEquals(false, PerformanceUiContext().isDeckRowFx("A", null))
    }

    // -- Text fitting -----------------------------------------------------------------------

    @Test
    fun ellipsizeShortensOnlyWhatDoesNotFit() {
        val measure: (String) -> Float = { it.length.toFloat() }
        assertEquals("Short", TextFit.ellipsize("Short", 10f, measure))
        assertEquals("Very lon…", TextFit.ellipsize("Very long name", 9f, measure))
        assertEquals(12f, TextFit.centeredY(10f, 20f, 16f))
    }
}
