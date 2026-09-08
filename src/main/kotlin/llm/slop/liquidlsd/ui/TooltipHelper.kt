package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiStyleVar

/**
 * Mixxx-inspired cursor-relative quadrant tooltip positioning and hover delay helper.
 *
 * Positions tooltips directly beneath an imaginary cursor bounding box (16x22px),
 * left-aligned with the cursor arrow hotspot. If approaching the right edge of the
 * viewport, the tooltip flips its alignment so its right edge is flush with the
 * cursor box's right edge (protruding to the left). If approaching the bottom of
 * the viewport, it flips vertically above the cursor.
 *
 * Uses [ImGuiCond.Always] — Dear ImGui's own BeginTooltip() internally calls
 * setNextWindowPos with Always, so any weaker condition would be silently overridden.
 *
 * Custom-content tooltips store up to [CACHE_SIZE] distinct rendered sizes in a
 * fixed-length circular array (zero allocation). On a cache miss the flip decision
 * defaults to no-flip (0×0 size estimate), which keeps the tooltip near the cursor
 * for that one frame rather than at the top of the screen.
 *
 * Also provides a lightweight, zero-allocation hover delay tracker (~250ms) to
 * prevent flickering when sweeping the cursor across controls.
 */
object TooltipHelper {

    const val CURSOR_BOX_WIDTH = 16f
    const val CURSOR_BOX_HEIGHT = 22f
    const val GAP_Y = 4f
    /** Viewport-edge guard band used in overflow detection. */
    const val PADDING = 8f
    /** Inner padding between tooltip text/content and the window border. */
    const val TOOLTIP_WINDOW_PADDING_X = 8f
    const val TOOLTIP_WINDOW_PADDING_Y = 6f
    const val DEFAULT_HOVER_DELAY_MS = 250L

    /**
     * Number of distinct custom-tooltip sizes remembered simultaneously.
     * Keeps a rolling window of recently-hovered items so that returning to a
     * parameter that was hovered in the last [CACHE_SIZE] distinct hovers is
     * always a cache hit.
     */
    private const val CACHE_SIZE = 8

    // ── Theme-base colours for tooltip isolation ────────────────────────────
    /**
     * Theme-default text colour for tooltip content. Set by UIThemeStyler so
     * tooltips remain readable regardless of which ImGuiCol.Text override is
     * active in the calling widget's push stack (e.g. BrowserDeckButtons deck colours).
     */
    var baseTextColor: Int = 0xFFE8E8E8.toInt()

    /**
     * Theme-default border colour for tooltip windows. Set by UIThemeStyler so
     * the tooltip border is not tinted by any per-widget ImGuiCol.Border push
     * active at call time.
     */
    var baseBorderColor: Int = 0xFF404040.toInt()

    data class TooltipPosResult(
        val targetX: Float,
        val targetY: Float,
        val pivotX: Float,
        val pivotY: Float
    )

    // ── Hover delay state ───────────────────────────────────────────────────
    private var activeHoverKey: Int = 0
    private var hoverStartTimeMs: Long = 0L
    private var lastHoverFrame: Int = -1

    // ── 8-slot circular size cache (zero allocation) ────────────────────────
    private val cacheKeys    = IntArray(CACHE_SIZE)   { 0 }
    private val cacheWidths  = FloatArray(CACHE_SIZE) { 0f }
    private val cacheHeights = FloatArray(CACHE_SIZE) { 0f }
    private var cacheNextSlot = 0

    /**
     * Pure geometric calculation of tooltip position and pivot.
     * Separated for deterministic unit testing without requiring an active GLFW/OpenGL context.
     */
    fun calculateTooltipPos(
        mouseX: Float,
        mouseY: Float,
        tipWidth: Float,
        tipHeight: Float,
        vpLeft: Float,
        vpTop: Float,
        vpRight: Float,
        vpBottom: Float,
        cursorWidth: Float = CURSOR_BOX_WIDTH,
        cursorHeight: Float = CURSOR_BOX_HEIGHT,
        gapY: Float = GAP_Y,
        padding: Float = PADDING
    ): TooltipPosResult {
        // Horizontal:
        // By default, tooltip is beneath the pointer box, left edge aligned with mouseX.
        // If placing it with left edge at mouseX causes its right edge (mouseX + tipWidth)
        // to exceed (vpRight - padding), align its right edge with (mouseX + cursorWidth).
        val wouldOverflowRight = (mouseX + tipWidth + padding) > vpRight
        var (targetX, pivotX) = if (wouldOverflowRight) {
            (mouseX + cursorWidth) to 1.0f
        } else {
            mouseX to 0.0f
        }

        // Clamp horizontal:
        if (pivotX == 1.0f && (targetX - tipWidth) < (vpLeft + padding)) {
            targetX = vpLeft + padding
            pivotX = 0.0f
        } else if (pivotX == 0.0f && targetX < (vpLeft + padding)) {
            targetX = vpLeft + padding
        }

        // Vertical:
        // By default, tooltip is beneath the pointer: targetY = mouseY + cursorHeight + gapY, pivotY = 0.0f.
        // If it overflows vpBottom, flip above the cursor box: targetY = mouseY - gapY, pivotY = 1.0f.
        val wouldOverflowBottom = (mouseY + cursorHeight + gapY + tipHeight + padding) > vpBottom
        var (targetY, pivotY) = if (wouldOverflowBottom) {
            (mouseY - gapY) to 1.0f
        } else {
            (mouseY + cursorHeight + gapY) to 0.0f
        }

        // Clamp vertical:
        if (pivotY == 1.0f && (targetY - tipHeight) < (vpTop + padding)) {
            targetY = vpTop + padding
            pivotY = 0.0f
        } else if (pivotY == 0.0f && targetY < (vpTop + padding)) {
            targetY = vpTop + padding
        }

        return TooltipPosResult(targetX, targetY, pivotX, pivotY)
    }

    /**
     * Checks whether an item hover duration has met the delay threshold.
     */
    fun shouldShowTooltip(key: Int, delayMs: Long, currentFrame: Int, currentTimeMs: Long): Boolean {
        if (delayMs <= 0L) return true

        if (currentFrame > lastHoverFrame + 1) {
            activeHoverKey = key
            hoverStartTimeMs = currentTimeMs
            lastHoverFrame = currentFrame
            return false
        }

        lastHoverFrame = currentFrame

        if (activeHoverKey != key) {
            activeHoverKey = key
            hoverStartTimeMs = currentTimeMs
            return false
        }

        return (currentTimeMs - hoverStartTimeMs) >= delayMs
    }

    /**
     * Prepares next window position for tooltip using Mixxx geometry.
     * Computes the exact top-left coordinates directly so we always pass a (0, 0)
     * pivot to ImGui. This prevents Dear ImGui from applying pivot offsets against
     * an uninitialized (0, 0) window size on frame 1.
     */
    fun prepareTooltipPos(contentWidth: Float, contentHeight: Float) {
        val mouseX = ImGui.getMousePosX()
        val mouseY = ImGui.getMousePosY()
        val io = ImGui.getIO()
        val vpLeft = 0f
        val vpTop = 0f
        val vpRight = io.displaySizeX
        val vpBottom = io.displaySizeY

        val res = calculateTooltipPos(
            mouseX = mouseX,
            mouseY = mouseY,
            tipWidth = contentWidth,
            tipHeight = contentHeight,
            vpLeft = vpLeft,
            vpTop = vpTop,
            vpRight = vpRight,
            vpBottom = vpBottom
        )

        val finalX = (res.targetX - contentWidth * res.pivotX).coerceIn(
            vpLeft + PADDING,
            (vpRight - contentWidth - PADDING).coerceAtLeast(vpLeft + PADDING)
        )
        val finalY = (res.targetY - contentHeight * res.pivotY).coerceIn(
            vpTop + PADDING,
            (vpBottom - contentHeight - PADDING).coerceAtLeast(vpTop + PADDING)
        )

        ImGui.setNextWindowPos(finalX, finalY, ImGuiCond.Always)
    }

    // ── Cache accessors ─────────────────────────────────────────────────────

    fun getCachedCustomSize(key: Int): Pair<Float, Float>? {
        for (i in 0 until CACHE_SIZE) {
            if (cacheKeys[i] == key && cacheWidths[i] >= 100f && cacheHeights[i] >= 30f) {
                return cacheWidths[i] to cacheHeights[i]
            }
        }
        return null
    }

    fun recordCustomSize(key: Int, width: Float, height: Float) {
        if (width < 100f || height < 30f) return
        // Update existing slot if present
        for (i in 0 until CACHE_SIZE) {
            if (cacheKeys[i] == key) {
                cacheWidths[i] = width
                cacheHeights[i] = height
                return
            }
        }
        // Write to next circular slot
        cacheKeys[cacheNextSlot] = key
        cacheWidths[cacheNextSlot] = width
        cacheHeights[cacheNextSlot] = height
        cacheNextSlot = (cacheNextSlot + 1) % CACHE_SIZE
    }

    fun resetHoverTimer() {
        activeHoverKey = 0
        hoverStartTimeMs = 0L
        lastHoverFrame = -1
    }
}

// ── Published helpers for public inline functions ───────────────────────────

/**
 * Pushes mandatory style overrides for every tooltip:
 * - Alpha = 1.0f (full opacity, immune to parent widget alpha)
 * - WindowPadding = (8, 6) px (consistent inner padding)
 * - Text colour = TooltipHelper.baseTextColor (immune to widget Text colour pushes)
 * - Border colour = TooltipHelper.baseBorderColor (immune to widget Border colour pushes)
 *
 * Balanced by [popTooltipStyles].
 */
@PublishedApi
internal fun pushTooltipStyles() {
    ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 1.0f)
    ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, TooltipHelper.TOOLTIP_WINDOW_PADDING_X, TooltipHelper.TOOLTIP_WINDOW_PADDING_Y)
    ImGui.pushStyleColor(ImGuiCol.Text,   TooltipHelper.baseTextColor)
    ImGui.pushStyleColor(ImGuiCol.Border, TooltipHelper.baseBorderColor)
}

/** Pops the two style vars and two colours pushed by [pushTooltipStyles]. */
@PublishedApi
internal fun popTooltipStyles() {
    ImGui.popStyleColor(2)
    ImGui.popStyleVar(2)
}

// ── Public API ───────────────────────────────────────────────────────────────

/**
 * Displays a Mixxx-style tooltip for the currently hovered item with standard hover delay.
 * Automatically checks [UITheme.tooltipsEnabled] and [ImGui.isItemHovered].
 */
fun itemTooltip(text: String, delayMs: Long = TooltipHelper.DEFAULT_HOVER_DELAY_MS) {
    if (!UITheme.tooltipsEnabled || !ImGui.isItemHovered()) return

    val minX = ImGui.getItemRectMinX().toInt()
    val minY = ImGui.getItemRectMinY().toInt()
    val key = (minX shl 16) xor (minY and 0xFFFF) xor text.hashCode()

    if (!TooltipHelper.shouldShowTooltip(key, delayMs, ImGui.getFrameCount(), System.currentTimeMillis())) return

    val padX = TooltipHelper.TOOLTIP_WINDOW_PADDING_X
    val padY = TooltipHelper.TOOLTIP_WINDOW_PADDING_Y
    val textSize = ImGui.calcTextSize(text)
    val width = textSize.x + padX * 2f
    val height = textSize.y + padY * 2f

    pushTooltipStyles()
    TooltipHelper.prepareTooltipPos(width, height)
    ImGui.setNextWindowBgAlpha(1.0f)
    ImGui.beginTooltip()
    ImGui.text(text)
    ImGui.endTooltip()
    popTooltipStyles()
}

/**
 * Displays a custom Mixxx-style tooltip with arbitrary ImGui content for the currently hovered item.
 * Automatically checks [UITheme.tooltipsEnabled] and [ImGui.isItemHovered].
 */
inline fun itemTooltip(
    delayMs: Long = TooltipHelper.DEFAULT_HOVER_DELAY_MS,
    estimatedWidth: Float = 320f,
    estimatedHeight: Float = 80f,
    block: () -> Unit
) {
    if (!UITheme.tooltipsEnabled || !ImGui.isItemHovered()) return

    val minX = ImGui.getItemRectMinX().toInt()
    val minY = ImGui.getItemRectMinY().toInt()
    val key = (minX shl 16) xor (minY and 0xFFFF)

    if (!TooltipHelper.shouldShowTooltip(key, delayMs, ImGui.getFrameCount(), System.currentTimeMillis())) return

    val cached = TooltipHelper.getCachedCustomSize(key)
    val width = cached?.first ?: estimatedWidth
    val height = cached?.second ?: estimatedHeight

    pushTooltipStyles()
    TooltipHelper.prepareTooltipPos(width, height)
    ImGui.setNextWindowBgAlpha(1.0f)
    ImGui.beginTooltip()
    block()
    val measuredW = ImGui.getWindowSizeX()
    val measuredH = ImGui.getWindowSizeY()
    if (measuredW >= 100f && measuredH >= 30f) {
        TooltipHelper.recordCustomSize(key, measuredW, measuredH)
    }
    ImGui.endTooltip()
    popTooltipStyles()
}

/**
 * Explicitly shows a Mixxx-style tooltip with text (bypassing isItemHovered, but respecting tooltipsEnabled).
 */
fun showTooltip(text: String, key: Int = text.hashCode(), delayMs: Long = TooltipHelper.DEFAULT_HOVER_DELAY_MS) {
    if (!UITheme.tooltipsEnabled) return
    if (!TooltipHelper.shouldShowTooltip(key, delayMs, ImGui.getFrameCount(), System.currentTimeMillis())) return

    val padX = TooltipHelper.TOOLTIP_WINDOW_PADDING_X
    val padY = TooltipHelper.TOOLTIP_WINDOW_PADDING_Y
    val textSize = ImGui.calcTextSize(text)
    val width = textSize.x + padX * 2f
    val height = textSize.y + padY * 2f

    pushTooltipStyles()
    TooltipHelper.prepareTooltipPos(width, height)
    ImGui.setNextWindowBgAlpha(1.0f)
    ImGui.beginTooltip()
    ImGui.text(text)
    ImGui.endTooltip()
    popTooltipStyles()
}

/**
 * Explicitly shows a custom Mixxx-style tooltip with arbitrary ImGui content.
 * Respects [UITheme.tooltipsEnabled] and tracks hover delay via [key].
 */
inline fun showCustomTooltip(
    key: Int,
    delayMs: Long = TooltipHelper.DEFAULT_HOVER_DELAY_MS,
    estimatedWidth: Float = 320f,
    estimatedHeight: Float = 120f,
    block: () -> Unit
) {
    if (!UITheme.tooltipsEnabled) return
    if (!TooltipHelper.shouldShowTooltip(key, delayMs, ImGui.getFrameCount(), System.currentTimeMillis())) return

    val cached = TooltipHelper.getCachedCustomSize(key)
    val width = cached?.first ?: estimatedWidth
    val height = cached?.second ?: estimatedHeight

    pushTooltipStyles()
    TooltipHelper.prepareTooltipPos(width, height)
    ImGui.setNextWindowBgAlpha(1.0f)
    ImGui.beginTooltip()
    block()
    val measuredW = ImGui.getWindowSizeX()
    val measuredH = ImGui.getWindowSizeY()
    if (measuredW >= 100f && measuredH >= 30f) {
        TooltipHelper.recordCustomSize(key, measuredW, measuredH)
    }
    ImGui.endTooltip()
    popTooltipStyles()
}

/**
 * Explicitly begins a Mixxx-style custom tooltip with estimated dimensions and 100% opacity guarantee.
 * Must be paired with [endCustomTooltip].
 */
fun beginCustomTooltip(estimatedWidth: Float = 240f, estimatedHeight: Float = 60f) {
    pushTooltipStyles()
    TooltipHelper.prepareTooltipPos(estimatedWidth, estimatedHeight)
    ImGui.setNextWindowBgAlpha(1.0f)
    ImGui.beginTooltip()
}

/**
 * Ends a custom tooltip started with [beginCustomTooltip] and pops the tooltip style overrides.
 */
fun endCustomTooltip(key: Int = 0) {
    if (key != 0) {
        TooltipHelper.recordCustomSize(key, ImGui.getWindowSizeX(), ImGui.getWindowSizeY())
    }
    ImGui.endTooltip()
    popTooltipStyles()
}
