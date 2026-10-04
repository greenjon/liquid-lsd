package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags

/**
 * One transient, non-interactive message at the bottom-centre of the window, visible from any view.
 * [show] replaces whatever is showing; [draw] is called once per frame from [UIManager], which also passes
 * in [llm.slop.liquidlsd.macro.MacroLearnState]'s status line ("Added target...", "Exported macro bank...")
 * as [draw]'s `fallback` -- `macro/` can't call into `ui/`, so the UI layer pulls it. A [show]n toast wins.
 */
object ToastOverlay {
    private var message: String? = null
    private var expiryMs: Long = 0L

    fun show(text: String, durationMs: Long = 5000L) {
        message = text
        expiryMs = System.currentTimeMillis() + durationMs
    }

    /** The message still on screen, or null once it has expired. */
    fun active(nowMs: Long = System.currentTimeMillis()): String? {
        val text = message ?: return null
        if (nowMs > expiryMs) { message = null; return null }
        return text
    }

    fun draw(displayWidth: Float, displayHeight: Float, fallback: String? = null) {
        val text = active() ?: fallback ?: return
        val flags = ImGuiWindowFlags.NoDecoration or ImGuiWindowFlags.NoInputs or
            ImGuiWindowFlags.NoSavedSettings or ImGuiWindowFlags.NoFocusOnAppearing or
            ImGuiWindowFlags.NoNav or ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoMove

        ImGui.setNextWindowPos(displayWidth * 0.5f, displayHeight - 48f, ImGuiCond.Always, 0.5f, 1f)
        ImGui.setNextWindowBgAlpha(0.92f)
        ImGui.pushStyleColor(ImGuiCol.Border, TangoPalette.u32(TangoPalette.ALERT.normal, 0.9f))
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 1.5f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 6f)
        if (ImGui.begin("##toast_overlay", flags)) ImGui.text(text)
        ImGui.end()
        ImGui.popStyleVar(2)
        ImGui.popStyleColor()
    }
}
