package llm.slop.liquidlsd.rack.ui

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rack.RackUnit
import llm.slop.liquidlsd.rendering.FBO
import llm.slop.liquidlsd.rendering.Renderer
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.showTooltip

/**
 * Embedded Confidence Micro-Monitor rendered on a rack module's faceplate (Milestone 6, Phase 7).
 *
 * Provides a tactile, hardware-styled confidence screen that displays the unit's immediate
 * visual output before downstream mixing or routing.
 *
 * Design characteristics:
 * - Clean, true video output: 1:1 texture presentation matching exact frame output without
 *   simulated CRT scanlines, barrel distortion, or artificial glass tinting.
 * - Hardware bezel: Recessed dark metallic chassis bezel with crisp inner border.
 * - Signal status: Displays unadulterated video when active, a clean black screen when powered off,
 *   or an amber BYPASS badge overlay when bypassed.
 */
object RackMicroMonitor {

    private const val BEZEL_PADDING = 3.0f

    // Shared downscaled preview resolution (§ Question 4 decision in
    // modular_video_rack_proposal.md) -- every unit's monitor is rendered at this fixed size
    // rather than sampling the full-res source texture directly.
    private const val PREVIEW_WIDTH = 240
    private const val PREVIEW_HEIGHT = 135

    // One small preview FBO per unit instance, lazily created and reused across frames. Released
    // via [releaseUnit]/[releaseAll] -- see call sites in RackPanel.kt.
    private val previewFbos = HashMap<String, FBO>()

    /**
     * Draws the confidence micro-monitor for the given [unit].
     *
     * @param session Current session context (for theme and aspect ratio).
     * @param unit Target rack unit to monitor.
     * @param monitorWidth Total outer width including the hardware bezel.
     * @param monitorHeight Total outer height including the hardware bezel.
     * @param renderer Renderer used to downscale-blit the unit's output into a shared preview
     *   resolution FBO before display. Pass `null` to skip downscaling and sample the full-res
     *   source texture directly (e.g. in a headless/test context with no live GL context).
     */
    fun draw(
        session: SessionContext,
        unit: RackUnit,
        monitorWidth: Float,
        monitorHeight: Float,
        renderer: Renderer? = null
    ) {
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()

        // 1. Hardware Bezel (recessed dark metallic housing)
        val outerBorderCol = ImGui.colorConvertFloat4ToU32(0.25f, 0.28f, 0.32f, 1.0f)
        val innerBezelBg = ImGui.colorConvertFloat4ToU32(0.08f, 0.09f, 0.10f, 1.0f)
        val bevelHighlightCol = ImGui.colorConvertFloat4ToU32(0.18f, 0.20f, 0.23f, 1.0f)

        dl.addRectFilled(startX, startY, startX + monitorWidth, startY + monitorHeight, innerBezelBg, 4.0f)
        dl.addRect(startX, startY, startX + monitorWidth, startY + monitorHeight, outerBorderCol, 4.0f, 0, 1.2f)

        // Inner screen region
        val screenX = startX + BEZEL_PADDING
        val screenY = startY + BEZEL_PADDING
        val screenW = (monitorWidth - (BEZEL_PADDING * 2f)).coerceAtLeast(10f)
        val screenH = (monitorHeight - (BEZEL_PADDING * 2f)).coerceAtLeast(10f)

        // Screen background (deep solid black)
        val screenBgCol = ImGui.colorConvertFloat4ToU32(0.02f, 0.02f, 0.03f, 1.0f)
        dl.addRectFilled(screenX, screenY, screenX + screenW, screenY + screenH, screenBgCol, 2.0f)

        // Subtle recessed top bevel
        dl.addLine(screenX, screenY, screenX + screenW, screenY, bevelHighlightCol, 1.0f)

        // 2. Video Texture Presentation
        val textureId = unit.lastOutputTexture
        val isPowered = unit.isPowered
        val isBypassed = unit.isBypassed

        if (isPowered && textureId > 0) {
            val previewTextureId = if (renderer != null) {
                val previewFbo = previewFbos.getOrPut(unit.id) { FBO(PREVIEW_WIDTH, PREVIEW_HEIGHT) }
                renderer.rescale(textureId, PREVIEW_WIDTH, PREVIEW_HEIGHT, previewFbo, UITheme.OutputScaleMode.STRETCH)
                previewFbo.texture
            } else {
                textureId
            }
            // ImGui uses inverted UVs for standard OpenGL textures (uv0=(0, 1), uv1=(1, 0))
            ImGui.setCursorScreenPos(screenX, screenY)
            ImGui.image(previewTextureId.toLong(), screenW, screenH, 0f, 1f, 1f, 0f)
        }

        // 3. Status Badges & Overlays
        if (!isPowered) {
            // Powered off overlay
            val offText = "STANDBY"
            drawCenterBadge(session, dl, screenX, screenY, screenW, screenH, offText, 0.40f, 0.42f, 0.46f)
        } else if (isBypassed) {
            // Semi-transparent dimming + BYPASS badge
            val dimCol = ImGui.colorConvertFloat4ToU32(0.05f, 0.05f, 0.05f, 0.70f)
            dl.addRectFilled(screenX, screenY, screenX + screenW, screenY + screenH, dimCol, 2.0f)
            drawCenterBadge(session, dl, screenX, screenY, screenW, screenH, "BYPASS", 0.95f, 0.55f, 0.15f)
        } else if (textureId <= 0) {
            // Powered on but no signal yet
            val noSigText = "NO SIGNAL"
            drawCenterBadge(session, dl, screenX, screenY, screenW, screenH, noSigText, 0.35f, 0.38f, 0.42f)
        }

        // Advance cursor past the monitor widget
        ImGui.setCursorScreenPos(startX, startY + monitorHeight)
        ImGui.dummy(0f, 0f)

        // Tooltip on hover
        val isHovered = ImGui.isMouseHoveringRect(startX, startY, startX + monitorWidth, startY + monitorHeight)
        if (isHovered) {
            val status = when {
                !isPowered -> "Powered OFF"
                isBypassed -> "Bypassed (Passthrough)"
                textureId > 0 -> "Active Output (GL Tex #$textureId)"
                else -> "Awaiting Input / Generator"
            }
            showTooltip(
                "Confidence Monitor: ${unit.label}\nStatus: $status\nSignal: ${unit.unitType.badgeLabel} -> Video Out",
                key = unit.id.hashCode()
            )
        }
    }

    /** Number of live preview FBOs currently cached (for the telemetry HUD's GPU-memory readout). */
    val previewFboCount: Int
        get() = previewFbos.size

    /** Disposes and releases the preview FBO for a single removed unit. */
    fun releaseUnit(unitId: String) {
        previewFbos.remove(unitId)?.dispose()
    }

    /** Disposes and releases every cached preview FBO (e.g. before a full rack re-sync). */
    fun releaseAll() {
        previewFbos.values.forEach { it.dispose() }
        previewFbos.clear()
    }

    private fun drawCenterBadge(
        session: SessionContext,
        dl: imgui.ImDrawList,
        screenX: Float,
        screenY: Float,
        screenW: Float,
        screenH: Float,
        text: String,
        r: Float,
        g: Float,
        b: Float
    ) {
        var tw = 0f
        var th = 0f
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val sz = ImGui.calcTextSize(text)
            tw = sz.x
            th = sz.y
        }

        val badgePadX = 6.0f
        val badgePadY = 2.0f
        val badgeW = tw + (badgePadX * 2f)
        val badgeH = th + (badgePadY * 2f)
        val bx = screenX + (screenW - badgeW) * 0.5f
        val by = screenY + (screenH - badgeH) * 0.5f

        val bgCol = ImGui.colorConvertFloat4ToU32(0.10f, 0.11f, 0.13f, 0.85f)
        val borderCol = ImGui.colorConvertFloat4ToU32(r, g, b, 0.80f)
        val textCol = ImGui.colorConvertFloat4ToU32(r, g, b, 1.0f)

        dl.addRectFilled(bx, by, bx + badgeW, by + badgeH, bgCol, 3.0f)
        dl.addRect(bx, by, bx + badgeW, by + badgeH, borderCol, 3.0f, 0, 1.0f)

        val tx = bx + badgePadX
        val ty = by + badgePadY
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            dl.addText(tx, ty, textCol, text)
        }
    }
}
