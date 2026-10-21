package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import llm.slop.liquidlsd.presets.DeckSlot
import llm.slop.liquidlsd.presets.PresetManager
import llm.slop.liquidlsd.rendering.ExternalVideoSource
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.TangoPalette
import java.io.File

/**
 * Which decks (and FX slots) a Library/Browse row is loaded on. A [Snapshot] is what each deck holds right now (a saved preset by
 * name, else a stock generator id, else an external stream name); [slotsFor] maps a row to the decks that hold it. A deck with
 * a saved preset loaded matches that preset's row only, not its generator's stock row, so one deck never marks two rows.
 */
internal object LoadedMarks {
    class Held(val presetName: String?, val sourceId: String?, val externalName: String?)

    class Snapshot(private val held: Map<DeckSlot, Held>) {
        fun marksFor(asset: AssetItem): List<Mark> = slotsFor(asset).map(::mark)

        fun slotsFor(asset: AssetItem): List<DeckSlot> = DeckSlot.entries.filter { slot ->
            val h = held[slot] ?: return@filter false
            when (asset.type) {
                AssetType.PRESET -> h.presetName != null && h.presetName == File(asset.path).nameWithoutExtension
                AssetType.SOURCE_STOCK -> h.presetName == null && h.sourceId == asset.path.removePrefix(BrowseCatalogs.STOCK_SOURCE_PREFIX)
                AssetType.SOURCE_EXTERNAL -> h.externalName != null && h.externalName == BrowseCatalogs.externalName(asset)
                else -> false
            }
        }
    }

    /** One chip in a row's gutter: a short [tag] in the holder's [rgb], with [desc] for the hover text. */
    class Mark(val tag: String, val rgb: FloatArray, val desc: String)

    private fun mark(slot: DeckSlot) = Mark(slot.name, color(slot), slot.label)

    /** One FX chain as held by a deck or the master bus: the saved chain file it came from and its slot effect ids. */
    class FxHeld(val tag: String, val rgb: FloatArray, val desc: String, val chainPath: String?, val slotIds: List<String?>)

    /** What the FX and Transition lists mark: every chain's stock effects per slot, saved chains by file, and the mixer's stock transition. */
    class FxSnapshot(private val chains: List<FxHeld>, private val transitionId: String?) {
        fun marksFor(asset: AssetItem): List<Mark> = when (asset.type) {
            AssetType.FX_STOCK -> {
                val id = asset.path.removePrefix(BrowseCatalogs.STOCK_FX_PREFIX)
                chains.flatMap { c -> c.slotIds.withIndex().filter { it.value == id }.map { Mark("${c.tag}${it.index + 1}", c.rgb, "${c.desc} slot ${it.index + 1}") } }
            }
            AssetType.FX_CHAIN -> chains.filter { it.chainPath != null && it.chainPath == asset.path }.map { Mark(it.tag, it.rgb, it.desc) }
            AssetType.TRANSITION_STOCK ->
                if (transitionId != null && asset.path.removePrefix(BrowseCatalogs.STOCK_TRANS_PREFIX) == transitionId) listOf(Mark("M", MASTER_RGB, "Transition")) else emptyList()
            else -> emptyList()
        }
    }

    private val MASTER_RGB = TangoPalette.NEUTRAL_LIGHT.normal

    fun fxSnapshot(mixer: Mixer): FxSnapshot {
        fun held(tag: String, rgb: FloatArray, desc: String, c: llm.slop.liquidlsd.rendering.FxChain) =
            FxHeld(tag, rgb, desc, c.sourceFile?.absolutePath, c.slots.map { it?.id })
        return FxSnapshot(
            DeckSlot.entries.map { held(it.name, color(it), it.label, it.deck(mixer).fxChain) } + held("M", MASTER_RGB, "Master FX", mixer.masterFxChain),
            mixer.transitionFilter?.id
        )
    }

    fun snapshot(mixer: Mixer, presets: PresetManager): Snapshot = Snapshot(DeckSlot.entries.associateWith { slot ->
        val source = slot.deck(mixer).source
        Held(presets.activePreset(slot), source.id, (source as? ExternalVideoSource)?.serverName)
    })

    /** Width of the badge gutter every row reserves, loaded or not, so names never shift. */
    const val GUTTER_W = 46f
    private const val CHIP_GAP = 2f
    private const val PAD_X = 3f

    private fun color(slot: DeckSlot): FloatArray = when (slot) {
        DeckSlot.A -> TangoPalette.DECK_A.normal
        DeckSlot.B -> TangoPalette.DECK_B.normal
        DeckSlot.BG -> TangoPalette.DECK_BG.normal
        DeckSlot.PV -> TangoPalette.DECK_PV.normal
    }

    /**
     * Reserves the row's gutter: returns its screen origin and leaves the cursor [GUTTER_W] further right on the same line.
     * Call before the row's selectable, then [drawGutter] right after it.
     */
    fun beginGutter(): Gutter {
        val g = Gutter(ImGui.getCursorScreenPosX(), ImGui.getCursorScreenPosY())
        ImGui.setCursorScreenPos(g.x + GUTTER_W, g.y)
        return g
    }

    class Gutter(val x: Float, val y: Float)

    /**
     * Draws the row's chips in the reserved [gutter]. If they do not all fit they collapse into one neutral "×n" chip; the hover
     * text always lists every holder. Drawn after the selectable on purpose: it highlights the whole row width, gutter included,
     * and would paint over chips drawn first.
     */
    fun drawGutter(gutter: Gutter, marks: List<Mark>, rowH: Float) {
        val x0 = gutter.x
        val y0 = gutter.y
        val dl = ImGui.getWindowDrawList()
        val chipH = ImGui.getTextLineHeight() + 2f
        val top = y0 + (rowH - chipH) * 0.5f
        val widths = marks.map { ImGui.calcTextSize(it.tag).x + 6f }
        val fits = widths.sum() + CHIP_GAP * (marks.size - 1) + PAD_X <= GUTTER_W
        val shown = if (fits) marks else if (marks.isEmpty()) marks else listOf(Mark("\u00D7${marks.size}", TangoPalette.TEXT_DIM.dark, ""))
        var x = x0 + PAD_X
        for (m in shown) {
            val w = ImGui.calcTextSize(m.tag).x + 6f
            dl.addRectFilled(x, top, x + w, top + chipH, TangoPalette.u32(m.rgb, 0.85f), 3f)
            dl.addText(x + 3f, top + 1f, TangoPalette.u32(floatArrayOf(1f, 1f, 1f, 1f)), m.tag)
            x += w + CHIP_GAP
        }
        if (marks.isNotEmpty() && ImGui.isWindowHovered() && ImGui.isMouseHoveringRect(x0, y0, x0 + GUTTER_W, y0 + rowH)) ImGui.setTooltip(tooltip(marks))
    }

    /** The tint behind a row that is loaded on the bound target; drawn before the selectable so the row's own states sit on top. */
    fun drawBoundFill(width: Float, rowH: Float) {
        val x = ImGui.getCursorScreenPosX()
        val y = ImGui.getCursorScreenPosY()
        ImGui.getWindowDrawList().addRectFilled(x, y, x + width, y + rowH, TangoPalette.u32(TangoPalette.ACTIVE.normal, 0.30f))
    }

    fun tooltip(marks: List<Mark>): String = "Loaded on " + marks.joinToString(", ") { it.desc }
}
