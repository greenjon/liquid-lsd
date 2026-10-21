package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.presets.DeckSlot
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import kotlin.test.Test
import kotlin.test.assertEquals

class LoadedMarksTest {
    private fun snap(vararg held: Pair<DeckSlot, LoadedMarks.Held>) = LoadedMarks.Snapshot(held.toMap())
    private fun stock(id: String) = AssetItem(BrowseCatalogs.STOCK_SOURCE_PREFIX + id, id, AssetType.SOURCE_STOCK)
    private fun preset(name: String) = AssetItem("library/presets/$name.lsd", name, AssetType.PRESET)

    @Test fun stockSourceMarksEveryDeckHoldingIt() {
        val s = snap(DeckSlot.A to LoadedMarks.Held(null, "noise", null), DeckSlot.BG to LoadedMarks.Held(null, "noise", null), DeckSlot.B to LoadedMarks.Held(null, "plasma", null))
        assertEquals(listOf(DeckSlot.A, DeckSlot.BG), s.slotsFor(stock("noise")))
    }

    @Test fun savedPresetMatchesByNameAndHidesItsStockGenerator() {
        val s = snap(DeckSlot.B to LoadedMarks.Held("Dream", "noise", null))
        assertEquals(listOf(DeckSlot.B), s.slotsFor(preset("Dream")))
        assertEquals(emptyList(), s.slotsFor(stock("noise")))
        assertEquals(emptyList(), s.slotsFor(preset("Other")))
    }

    @Test fun emptyDeckMarksNothing() {
        assertEquals(emptyList(), snap().slotsFor(stock("noise")))
    }

    private val rgb = floatArrayOf(1f, 0f, 0f, 1f)
    private fun fx(id: String) = AssetItem(BrowseCatalogs.STOCK_FX_PREFIX + id, id, AssetType.FX_STOCK)

    @Test fun stockFxMarksEachChainSlotHoldingIt() {
        val s = LoadedMarks.FxSnapshot(listOf(
            LoadedMarks.FxHeld("A", rgb, "Deck A", null, listOf("blur", null, "blur")),
            LoadedMarks.FxHeld("M", rgb, "Master FX", null, listOf(null, "blur", null)),
        ), null)
        assertEquals(listOf("A1", "A3", "M2"), s.marksFor(fx("blur")).map { it.tag })
        assertEquals(emptyList(), s.marksFor(fx("glow")).map { it.tag })
    }

    @Test fun savedChainMatchesByFileAndTransitionByStockId() {
        val s = LoadedMarks.FxSnapshot(listOf(LoadedMarks.FxHeld("B", rgb, "Deck B", "/lib/x.lsdfxchain", listOf(null, null, null))), "wipe")
        assertEquals(listOf("B"), s.marksFor(AssetItem("/lib/x.lsdfxchain", "x", AssetType.FX_CHAIN)).map { it.tag })
        assertEquals(listOf("M"), s.marksFor(AssetItem(BrowseCatalogs.STOCK_TRANS_PREFIX + "wipe", "wipe", AssetType.TRANSITION_STOCK)).map { it.tag })
        assertEquals(emptyList(), s.marksFor(AssetItem(BrowseCatalogs.STOCK_TRANS_PREFIX + "fade", "fade", AssetType.TRANSITION_STOCK)).map { it.tag })
    }
}
