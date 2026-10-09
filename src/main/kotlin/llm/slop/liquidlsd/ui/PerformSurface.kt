package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.control.KnobCommands
import llm.slop.liquidlsd.control.KnobLight
import llm.slop.liquidlsd.control.KnobLightSource
import llm.slop.liquidlsd.control.KnobSurface
import llm.slop.liquidlsd.control.NavSurface
import llm.slop.liquidlsd.control.SendTarget
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroControl
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.parameters.MeterType
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.rendering.Mixer

/** One addressable knob of the Perform view: its row's bank plus what the knob currently shows. */
internal data class PageKnob(val bankId: String, val spec: KnobSpec, val accent: FloatArray) {
    val control: MacroControl get() = spec.control
}

/** The 16 knobs (row-major over the visible rows) that a hardware controller addresses; null where a row has no knob. */
internal class PerformPage(val knobs: List<PageKnob?>)

internal object PerformPages {
    private const val COLS = 4

    /** The LED colour for [row]: its accent, except the greyscale Master and Global rows (see [PerformanceColors.LED_MASTER]). */
    fun ledColor(row: RowDescriptor): FloatArray {
        val base = when (row.bankId) {
            MacroEngine.MASTER, MacroEngine.MASTER_FX -> PerformanceColors.LED_MASTER
            MacroEngine.GLOBAL -> PerformanceColors.LED_GLOBAL
            else -> row.accent
        }
        // A pinned FX row sits beside its deck's (or Master's) SRC/MIX row on one page; a hue shift tells them apart on the LEDs.
        if (row.pinnedMode != "FX") return base
        val shift = if (row.bankId == MacroEngine.MASTER_FX) -FX_HUE_SHIFT else FX_HUE_SHIFT
        return rotateHue(base, shift)
    }

    /** How far a pinned FX row's LED hue moves from its SRC row's (degrees); Master goes the other way to stay clear of Wet/Dry. */
    private const val FX_HUE_SHIFT = 30f

    private fun rotateHue(rgb: FloatArray, degrees: Float): FloatArray {
        val hsb = java.awt.Color.RGBtoHSB((rgb[0] * 255f).toInt(), (rgb[1] * 255f).toInt(), (rgb[2] * 255f).toInt(), null)
        val h = ((hsb[0] + degrees / 360f) % 1f + 1f) % 1f
        val c = java.awt.Color(java.awt.Color.HSBtoRGB(h, hsb[1], hsb[2]))
        return floatArrayOf(c.red / 255f, c.green / 255f, c.blue / 255f)
    }

    /**
     * Resolves the page the way [PerformanceMatrixPanel] draws it: the rows [PerfRows.visibleRowsForPage]
     * returns (the page's four rows, or the open module's row in Deep Edit), each through [PerfKnobResolver].
     */
    fun resolve(
        pageId: String,
        ctx: PerformanceUiContext,
        parametersState: ParametersState,
        mixer: Mixer,
        rowsCache: PerfRows.RowsCache? = null
    ): PerformPage {
        val knobs = arrayOfNulls<PageKnob>(KnobCommands.KNOB_COUNT)
        resolveInto(pageId, ctx, parametersState, mixer, rowsCache, knobs)
        return PerformPage(knobs.toList())
    }

    /**
     * [resolve] into a caller-owned [knobs] array of [KnobCommands.KNOB_COUNT] (cleared first); rows come from [rowsCache] when given.
     * Returns the index of the Clock knob (first knob of the Global row, which has no macro knobs of its own), or -1 when the page has no Clock row.
     */
    fun resolveInto(
        pageId: String,
        ctx: PerformanceUiContext,
        parametersState: ParametersState,
        mixer: Mixer,
        rowsCache: PerfRows.RowsCache?,
        knobs: Array<PageKnob?>
    ): Int {
        var clockKnob = -1
        java.util.Arrays.fill(knobs, null)
        val pages = PerfPageStore.default.all()
        val page = pages.firstOrNull { it.id == pageId } ?: pages.first()
        val rows = if (rowsCache != null) rowsCache.rows(page, ctx, parametersState, IDENTITY, pages)
        else PerfRows.visibleRowsForPage(page, ctx, parametersState, IDENTITY, pages)
        val rowCount = minOf(rows.size, KnobCommands.KNOB_COUNT / COLS)
        for (rowIdx in 0 until rowCount) {
            val row = rows[rowIdx]
            if (row.bankId == MacroEngine.GLOBAL) clockKnob = rowIdx * COLS
            val bank = MacroEngine.getBank(row.bankId) ?: MacroEngine.bankForParamPath(row.bankId)
            val isFxBank = row.bankId in FxMacroSync.FX_BANK_IDS
            val chain = if (isFxBank && row.hasExtraHeader) ctx.resolveFxChain(mixer, row.bankId) else null
            for (spec in PerfKnobResolver.resolve(bank, row.knobOffset, chain?.let { FxRowState.of(it) }, mixer)) {
                knobs[rowIdx * COLS + spec.col] = PageKnob(row.bankId, spec, ledColor(row))
            }
        }
        return clockKnob
    }

    private val IDENTITY: (String) -> String = { it }
}

/**
 * Applies hardware knob gestures to the live Perform view. Each call re-resolves the page, so a row
 * flipping between SRC/FX or into FX focus retargets the same physical knob immediately.
 */
internal class PerformSurface(
    private val theme: UITheme,
    private val ctx: PerformanceUiContext,
    private val parametersState: ParametersState,
    private val mixer: Mixer,
    private val nav: NavSurface? = null,
    private val clock: ClockKnobFeed? = null
) : KnobSurface, KnobLightSource {

    private val rowsCache = PerfRows.RowsCache()
    private val knobBuffer = arrayOfNulls<PageKnob>(KnobCommands.KNOB_COUNT)
    private val lightBuffer = ArrayList<KnobLight?>(KnobCommands.KNOB_COUNT)
    /** The Clock knob of the page [knobBuffer] holds (-1 if none): it shows the tempo, taps on push and ignores turns. */
    private var clockKnob = -1

    /** Fresh specs every call (chain state is live), but the page's rows come from the cache. Callers run on one thread at a time. */
    @Synchronized
    private fun knob(index: Int): PageKnob? {
        if (index !in knobBuffer.indices) return null
        clockKnob = PerformPages.resolveInto(theme.performancePageId, ctx, parametersState, mixer, rowsCache, knobBuffer)
        return knobBuffer[index]
    }

    @Synchronized
    override fun pressDown(knob: Int): Boolean {
        if (clock == null || knob !in knobBuffer.indices) return false
        clockKnob = PerformPages.resolveInto(theme.performancePageId, ctx, parametersState, mixer, rowsCache, knobBuffer)
        if (knob != clockKnob) return false
        clock.tap()
        return true
    }

    override fun turn(knob: Int, delta: Float) {
        lastTouchedKnob = knob
        val control = knob(knob)?.control ?: return
        control.value = (control.value + delta).coerceIn(0f, 1f)
    }

    override fun primary(knob: Int) {
        lastTouchedKnob = knob
        val target = knob(knob) ?: return
        when (val under = target.spec.under) {
            is UnderKnob.SlotCell -> toggleBypass(target.bankId, under.slotIndex)
            is UnderKnob.ParamCell -> under.param?.let { resetParameter(target.control, it) }
            is UnderKnob.Label -> target.control.value = LABEL_KNOB_DEFAULT
        }
    }

    override fun secondary(knob: Int) {
        lastTouchedKnob = knob
        val target = knob(knob) ?: return
        when (val under = target.spec.under) {
            is UnderKnob.SlotCell -> {
                // Group mode: knobs 2-4 focus their slot. Focus mode: knob 1 leaves focus.
                val leaving = target.spec.side is SideButtons.Bypass
                FxMacroSync.focusSlot(target.bankId, mixer, if (leaving) null else under.slotIndex)
            }
            is UnderKnob.ParamCell -> if (under.param != null) FxMacroSync.stepParamPage(target.bankId, mixer, +1)
            is UnderKnob.Label -> if (target.spec.side is SideButtons.ChainLink) {
                FxMacroSync.chainFor(target.bankId, mixer)?.toggleAllSlotsLinked()
                FxMacroSync.syncFor(target.bankId, mixer)
            }
        }
    }

    /**
     * Chain Link for the row touched last: an FX row's own chain, a deck row's FX chain, Master's FX chain.
     * With nothing touched yet, the first FX row on the page.
     */
    @Synchronized
    override fun toggleChainLink() {
        clockKnob = PerformPages.resolveInto(theme.performancePageId, ctx, parametersState, mixer, rowsCache, knobBuffer)
        val touched = lastTouchedKnob?.let { knobBuffer.getOrNull(it) }?.bankId
        val bankId = touched?.let { fxBankFor(it) } ?: knobBuffer.firstNotNullOfOrNull { it?.bankId?.let(::fxBankFor) } ?: return
        FxMacroSync.chainFor(bankId, mixer)?.toggleAllSlotsLinked()
        FxMacroSync.syncFor(bankId, mixer)
    }

    private fun fxBankFor(bankId: String): String? = when (bankId) {
        in FxMacroSync.FX_BANK_IDS -> bankId
        MacroEngine.DECK_A -> MacroEngine.DECK_A_FX
        MacroEngine.DECK_B -> MacroEngine.DECK_B_FX
        MacroEngine.DECK_BG -> MacroEngine.DECK_BG_FX
        MacroEngine.DECK_PV -> MacroEngine.DECK_PV_FX
        MacroEngine.MASTER -> MacroEngine.MASTER_FX
        else -> null
    }

    override val pairFocused: Boolean get() = parametersState.focusedPair != null

    override fun stepPair(delta: Int): Boolean {
        val tag = parametersState.focusedPair ?: return false
        val pairs = PerfRows.PAIRS
        val from = pairs.indexOfFirst { it.tag == tag }.coerceAtLeast(0)
        parametersState.focusPair(pairs[Math.floorMod(from + delta, pairs.size)].tag)
        return true
    }

    override val currentPageId: String? get() = theme.performancePageId

    override fun showPage(pageId: String) {
        // `perform.<id>` names a page; the page id is the built-in file's `id` (`ab`, `bgpv`, `mixer`).
        val id = pageId.removePrefix("perform.")
        if (PerfPageStore.default.get(id) == null) return
        parametersState.leaveEditForPageChange()
        theme.performancePageId = id
    }

    /**
     * Ring = the knob's value; LED = its row's accent colour, dark where nothing is there to
     * control (an empty or bypassed FX slot, a blank parameter page position).
     */
    @Synchronized
    override fun knobLights(): List<KnobLight?> {
        clockKnob = PerformPages.resolveInto(theme.performancePageId, ctx, parametersState, mixer, rowsCache, knobBuffer)
        // The returned list is reused on the next poll; the poller reads it before polling again.
        lightBuffer.clear()
        for (target in knobBuffer) {
            lightBuffer.add(target?.let {
                KnobLight(
                    value = it.control.value,
                    r = it.accent[0],
                    g = it.accent[1],
                    b = it.accent[2],
                    lit = isLit(it),
                    meterType = it.spec.meterType
                )
            })
        }
        if (clock != null && clockKnob >= 0) lightBuffer[clockKnob] = clock.light()
        if (nav?.browsing == true) dimForBrowse(nav.browseLiveKnobs, nav.sendTargets, nav.browsePosition)
        return lightBuffer
    }

    /** While browsing only the cursor knob (white) and, with the browsed rows on screen, the leading live knobs (their colour) stay lit. */
    private fun dimForBrowse(liveKnobs: Int, sendTargets: Set<SendTarget>, cursorPosition: Float) {
        for (i in lightBuffer.indices) {
            if (i == KnobCommands.BROWSE_KNOB) lightBuffer[i] = KnobLight(cursorPosition, meterType = MeterType.ENDLESS)
            else if (i >= liveKnobs) lightBuffer[i] = SendTarget.forKnob(i)?.takeIf { it in sendTargets }?.let(::sendLight)
        }
    }

    /** A live send knob glows in its target's colour (the Master row's LED hue for the master bus). */
    private fun sendLight(target: SendTarget): KnobLight {
        val c = when (target) {
            SendTarget.A -> PerformanceColors.COLOR_DECK_A
            SendTarget.B -> PerformanceColors.COLOR_DECK_B
            SendTarget.BG -> PerformanceColors.COLOR_DECK_BG
            SendTarget.PV -> PerformanceColors.COLOR_DECK_PV
            SendTarget.MASTER -> PerformanceColors.LED_MASTER
        }
        return KnobLight(1f, c[0], c[1], c[2], meterType = MeterType.ENDLESS, ringBrightness = SEND_RING_BRIGHTNESS, marker = true)
    }

    private fun isLit(target: PageKnob): Boolean = when (val under = target.spec.under) {
        is UnderKnob.SlotCell -> FxMacroSync.chainFor(target.bankId, mixer)?.slots?.getOrNull(under.slotIndex)?.enabled == true
        is UnderKnob.ParamCell -> under.param != null
        is UnderKnob.Label -> true
    }

    private fun toggleBypass(bankId: String, slotIndex: Int) {
        val chain = FxMacroSync.chainFor(bankId, mixer) ?: return
        val slot = chain.slots.getOrNull(slotIndex) ?: return
        FxOps.setSlotEnabled(chain, slotIndex, !slot.enabled)
    }

    /** Same as the focus-mode reset button: the parameter returns to its default and the knob follows. */
    private fun resetParameter(control: MacroControl, param: ModulatableParameter) {
        param.baseValue = param.defaultValue
        val range = param.maxClamp - param.minClamp
        control.value = if (range > 0f) ((param.baseValue - param.minClamp) / range).coerceIn(0f, 1f) else 0f
    }

    companion object {

        /** What a mouse middle-click does on these knobs (the matrix passes 0.5 as every macro's default). */
        const val LABEL_KNOB_DEFAULT = 0.5f

        /** A live send knob lights its whole ring at this brightness (a full ring, so it reads as different from a value). */
        const val SEND_RING_BRIGHTNESS = 0.5f

        /** The knob (0-based) a controller touched last; the controller's "pick" button opens the picker of its row. */
        @Volatile var lastTouchedKnob: Int? = null
    }
}
