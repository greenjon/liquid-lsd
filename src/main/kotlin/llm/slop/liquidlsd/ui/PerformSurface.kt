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
import llm.slop.liquidlsd.ui.browser.QueueDrop
import llm.slop.liquidlsd.ui.browser.QueueKnobs
import llm.slop.liquidlsd.ui.browser.QueueOps
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
        val base = when (FxMacroSync.chainBankIdOf(row.bankId) ?: row.bankId) {
            MacroEngine.MASTER, MacroEngine.MASTER_FX -> PerformanceColors.LED_MASTER
            MacroEngine.GLOBAL -> PerformanceColors.LED_GLOBAL
            else -> row.accent
        }
        // A pinned FX row sits beside its deck's (or Master's) SRC/MST row on one page; a hue shift tells them apart on the LEDs.
        // Chain view's three slot rows step the shift (20/30/40 degrees) so they read apart from each other too.
        if (row.pinnedMode != "FX") return base
        val magnitude = row.slotIndex?.let { SLOT_HUE_SHIFTS[it.coerceIn(0, SLOT_HUE_SHIFTS.lastIndex)] } ?: FX_HUE_SHIFT
        val isMasterChain = (FxMacroSync.chainBankIdOf(row.bankId) ?: row.bankId) == MacroEngine.MASTER_FX
        return rotateHue(base, if (isMasterChain) -magnitude else magnitude)
    }

    /** How far a pinned FX row's LED hue moves from its SRC row's (degrees); Master goes the other way to stay clear of Wet/Dry. */
    private const val FX_HUE_SHIFT = 30f

    /** [FX_HUE_SHIFT] per Chain-view slot row. */
    private val SLOT_HUE_SHIFTS = floatArrayOf(20f, 30f, 40f)

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
        knobs: Array<PageKnob?>,
        /** Rows to resolve instead of the page's (the Library view's live pair). */
        rowsOverride: List<RowDescriptor>? = null
    ): Int {
        var clockKnob = -1
        java.util.Arrays.fill(knobs, null)
        val pages = PerfPageStore.default.all()
        val page = pages.firstOrNull { it.id == pageId } ?: pages.first()
        val rows = rowsOverride ?: if (rowsCache != null) rowsCache.rows(page, ctx, parametersState, IDENTITY, pages)
        else PerfRows.visibleRowsForPage(page, ctx, parametersState, IDENTITY, pages)
        val rowCount = minOf(rows.size, KnobCommands.KNOB_COUNT / COLS)
        for (rowIdx in 0 until rowCount) {
            val row = rows[rowIdx]
            // Chain view's load row has no macro knobs; the controller's browse handling owns those four knobs.
            if (row.loadRow) continue
            if (row.bankId == MacroEngine.GLOBAL) clockKnob = rowIdx * COLS
            val bank = MacroEngine.getBank(row.bankId) ?: MacroEngine.bankForParamPath(row.bankId)
            for (spec in PerfKnobResolver.resolve(bank, row.knobOffset, FxRowState.forRow(row, mixer), mixer)) {
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
    private val clock: ClockKnobFeed? = null,
    /** Time source for the pickup blink; tests pin it. */
    private val nowNanos: () -> Long = { llm.slop.liquidlsd.utils.TimeSource.getTimeNanos() }
) : KnobSurface, KnobLightSource {

    private val rowsCache = PerfRows.RowsCache()
    private val knobBuffer = arrayOfNulls<PageKnob>(KnobCommands.KNOB_COUNT)
    private val lightBuffer = ArrayList<KnobLight?>(KnobCommands.KNOB_COUNT)
    /** The Clock knob of the page [knobBuffer] holds (-1 if none): it shows the tempo, taps on push and ignores turns. */
    private var clockKnob = -1

    private var liveTag: String? = null
    private var liveRows: List<RowDescriptor> = emptyList()

    /**
     * The Library view hides the page, so knobs 1-8 play the live pair there ([ParametersState.libraryLivePair]): the deck the last
     * send loaded, PV to begin with. Null elsewhere (the page's rows, or the pair view's).
     */
    private fun libraryLiveRows(): List<RowDescriptor>? {
        if (theme.libraryMode != UITheme.LibraryMode.FULL || parametersState.focusedPair != null) return null
        val tag = parametersState.libraryLivePair
        if (tag != liveTag) {
            liveTag = tag
            liveRows = PerfRows.pairFor(tag)?.let(PerfRows::rowsForPair) ?: emptyList()
        }
        return liveRows
    }

    private fun resolvePage(): Int =
        PerformPages.resolveInto(theme.performancePageId, ctx, parametersState, mixer, rowsCache, knobBuffer, libraryLiveRows())

    /** Fresh specs every call (chain state is live), but the page's rows come from the cache. Callers run on one thread at a time. */
    @Synchronized
    private fun knob(index: Int): PageKnob? {
        if (index !in knobBuffer.indices) return null
        clockKnob = resolvePage()
        return knobBuffer[index]
    }

    @Synchronized
    override fun pressDown(knob: Int): Boolean {
        if (clock == null || knob !in knobBuffer.indices) return false
        clockKnob = resolvePage()
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
            is UnderKnob.ParamCell -> under.param?.let { FxMacroSync.resetFocusParam(target.control, it) }
            // The Super Knob (knob 1 of an FX row in group mode) ignores a tap: resetting it would move every linked slot at once.
            is UnderKnob.Label -> if (target.spec.side !is SideButtons.ChainLink) target.control.value = LABEL_KNOB_DEFAULT
        }
    }

    override fun secondary(knob: Int) {
        lastTouchedKnob = knob
        val target = knob(knob) ?: return
        when (val under = target.spec.under) {
            is UnderKnob.SlotCell -> {
                // A Chain-view slot row shows every slot at once, so focusing means nothing there.
                if (FxMacroSync.slotBankTarget(target.bankId) != null) return
                // Group mode: knobs 2-4 focus their slot. Focus mode: knob 1 leaves focus.
                val leaving = target.spec.side is SideButtons.Bypass
                FxMacroSync.focusSlot(target.bankId, mixer, if (leaving) null else under.slotIndex)
            }
            is UnderKnob.ParamCell -> if (under.param != null) FxMacroSync.stepParamPage(target.bankId, mixer, +1)
            is UnderKnob.Label -> if (target.spec.side is SideButtons.ChainLink) {
                FxMacroSync.chainFor(target.bankId, mixer)?.toggleAllSlotsLinked()
                FxMacroSync.syncFor(target.bankId, mixer)
            } else {
                // A deck's SRC row (Master: the Transitions row): Chain view on or off for that deck.
                chainTagFor(target.bankId)?.let { parametersState.toggleChainView(it) }
            }
        }
    }

    /**
     * Link All for the row touched last: an FX row's own chain, a deck row's FX chain, Master's FX chain.
     * With nothing touched yet, the first FX row on the page.
     */
    @Synchronized
    override fun toggleChainLink() {
        clockKnob = resolvePage()
        val touched = lastTouchedKnob?.let { knobBuffer.getOrNull(it) }?.bankId
        val bankId = touched?.let { fxBankFor(it) } ?: knobBuffer.firstNotNullOfOrNull { it?.bankId?.let(::fxBankFor) } ?: return
        FxMacroSync.chainFor(bankId, mixer)?.toggleAllSlotsLinked()
        FxMacroSync.syncFor(bankId, mixer)
    }

    private fun fxBankFor(bankId: String): String? = FxMacroSync.chainBankIdOf(bankId) ?: when (bankId) {
        MacroEngine.DECK_A -> MacroEngine.DECK_A_FX
        MacroEngine.DECK_B -> MacroEngine.DECK_B_FX
        MacroEngine.DECK_BG -> MacroEngine.DECK_BG_FX
        MacroEngine.DECK_PV -> MacroEngine.DECK_PV_FX
        MacroEngine.MASTER -> MacroEngine.MASTER_FX
        else -> null
    }

    /** The pair whose Chain view a shift + tap on a [bankId] knob toggles: a deck's source row or Transitions, not an FX row. */
    private fun chainTagFor(bankId: String): String? =
        if (FxMacroSync.isFxBank(bankId)) null else PerfRows.pairForBank(bankId)?.tag?.takeIf { PerfRows.chainBankFor(it) != null }

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
     * control (an empty or bypassed FX slot, a blank parameter page position). A linked FX slot still
     * waiting for the Super Knob (Pickup mode) blinks its LED amber / dark and, where the device can
     * dim rings (stock firmware), shows a dimmed ring; see [pickupLight].
     */
    @Synchronized
    override fun knobLights(): List<KnobLight?> {
        clockKnob = resolvePage()
        // The returned list is reused on the next poll; the poller reads it before polling again.
        lightBuffer.clear()
        val blinkOn = (nowNanos() / PICKUP_BLINK_HALF_PERIOD_NANOS) % 2L == 0L
        for (target in knobBuffer) {
            lightBuffer.add(target?.let {
                if (isAwaitingPickup(it)) pickupLight(it, blinkOn)
                else KnobLight(
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
        if (nav?.browsing == true) dimForBrowse(nav)
        return lightBuffer
    }

    /**
     * While browsing only the cursor knob (white), the live knobs of the browsed rows on screen (their colour), the live send knobs,
     * Chain view's load knobs and the Library view's queue knobs stay lit. The PV send knob blinks while auto-preview is on.
     */
    private fun dimForBrowse(nav: NavSurface) {
        val sendTargets = nav.sendTargets
        val chainRows = if (parametersState.chainView == ParametersState.ChainViewState.BROWSE)
            parametersState.focusedPair?.let { PerfRows.chainRowsFor(it, ParametersState.ChainViewState.BROWSE) } else null
        val queueTab = if (QueueKnobs.live()) LibraryPanel.viewMode else null
        for (i in lightBuffer.indices) {
            if (i == nav.browseKnob) lightBuffer[i] = KnobLight(nav.browsePosition, meterType = MeterType.ENDLESS)
            else if (!nav.isBrowseLive(i)) {
                // Chain view's load knobs glow in the colour of the slot row they load into; dark while the cursor item can't go in a slot.
                val slotRow = chainRows?.getOrNull(i + 1)?.takeIf { i < chainRows.size - 1 }
                val queue = queueTab?.let { QueueKnobs.queueFor(i, it) }
                lightBuffer[i] = when {
                    slotRow != null -> loadLight(slotRow)
                    queue != null -> queueLight(queue)
                    i == SendTarget.PV.knob && LibraryNavigation.autoPreview -> autoPreviewLight()
                    else -> SendTarget.forKnob(i)?.takeIf { it in sendTargets }?.let(::sendLight)
                }
            }
        }
    }

    /**
     * A queue knob: its queue's colour (dark while the queue is empty), the ring at the cursor's place in the queue (full with no cursor
     * or one row), full for a moment when items arrive ([QueueDrop.flashAmount]).
     */
    private fun queueLight(queue: QueueDrop.Queue): KnobLight {
        val c = when (queue) {
            QueueDrop.Queue.AB, QueueDrop.Queue.FX_AB -> TangoPalette.QUEUE_AB_TEXT.dark
            QueueDrop.Queue.BG, QueueDrop.Queue.FX_BG -> TangoPalette.QUEUE_BG_TEXT.dark
            QueueDrop.Queue.TRANS -> PerformanceColors.COLOR_TRANS
        }
        val size = QueueOps.items(queue).size
        val cursor = QueueOps.cursor(queue)
        val position = if (size <= 1 || cursor < 0) 1f else cursor.toFloat() / (size - 1)
        val arriving = QueueDrop.flashAmount(queue) > 0f
        return KnobLight(if (arriving) 1f else position, c[0], c[1], c[2], lit = size > 0, meterType = MeterType.ENDLESS)
    }

    /** The PV send knob while auto-preview is on: blinks in PV's colour, whatever the cursor item. */
    private fun autoPreviewLight(): KnobLight {
        val c = PerformanceColors.COLOR_DECK_PV
        val on = (nowNanos() / PICKUP_BLINK_HALF_PERIOD_NANOS) % 2L == 0L
        return KnobLight(1f, c[0], c[1], c[2], lit = on, meterType = MeterType.ENDLESS, ringBrightness = SEND_RING_BRIGHTNESS, marker = true)
    }

    private fun loadLight(slotRow: RowDescriptor): KnobLight {
        val c = PerformPages.ledColor(slotRow)
        return KnobLight(1f, c[0], c[1], c[2], lit = ChainLoad.cursorAsset() != null, meterType = MeterType.ENDLESS,
            ringBrightness = SEND_RING_BRIGHTNESS, marker = true)
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

    private fun isAwaitingPickup(target: PageKnob): Boolean {
        val slot = (target.spec.under as? UnderKnob.SlotCell)?.slotIndex ?: return false
        return FxMacroSync.chainFor(target.bankId, mixer)?.isAwaitingPickup(slot) == true
    }

    /**
     * A slot knob waiting for pickup: the ring still shows the Metaknob (dimmed where the device can dim it), and the LED
     * strobes amber / dark. Not row colour / amber: Deck A's orange is too close to amber on the Twister to read as a blink.
     */
    private fun pickupLight(target: PageKnob, blinkOn: Boolean): KnobLight = KnobLight(
        value = target.control.value,
        r = PICKUP_LED[0], g = PICKUP_LED[1], b = PICKUP_LED[2],
        lit = blinkOn,
        meterType = target.spec.meterType,
        ringBrightness = PICKUP_RING_BRIGHTNESS
    )

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

    companion object {

        /** What a mouse middle-click does on these knobs (the matrix passes 0.5 as every macro's default). */
        const val LABEL_KNOB_DEFAULT = 0.5f

        /** A live send knob lights its whole ring at this brightness (a full ring, so it reads as different from a value). */
        const val SEND_RING_BRIGHTNESS = 0.5f

        /** Ring brightness of a slot waiting for Super Knob pickup (devices that can dim rings only). */
        const val PICKUP_RING_BRIGHTNESS = 0.3f

        /** The pickup blink: 250 ms on, 250 ms off (2 Hz). */
        const val PICKUP_BLINK_HALF_PERIOD_NANOS = 250_000_000L

        /** The pickup blink's LED colour: the same caution amber as the on-screen link button. */
        val PICKUP_LED: FloatArray = TangoPalette.ALERT.normal

        /** The knob (0-based) a controller touched last; the controller's "pick" button opens the picker of its row. */
        @Volatile var lastTouchedKnob: Int? = null
    }
}
