package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.update.ReleaseInfo

/**
 * Modal prompt shown to the user when a new release is detected.
 *
 * Provides options to:
 * 1. Download Update (opens browser to the release page)
 * 2. Remind Me Later (dismisses prompt for the active session)
 * 3. Skip This Version (persists release tag in preferences so it is not prompted again)
 */
object UpdatePromptModal {

    private const val MODAL_ID = "Update Available###update_prompt_modal"

    private var pendingOpen = false
    private var latestRelease: ReleaseInfo? = null
    private var currentVersion: String = ""

    fun request(release: ReleaseInfo, currentVer: String) {
        latestRelease = release
        currentVersion = currentVer
        pendingOpen = true
    }

    fun draw(session: llm.slop.liquidlsd.SessionContext) {
        val release = latestRelease ?: return

        if (pendingOpen) {
            ImGui.openPopup(MODAL_ID)
            pendingOpen = false
        }

        val displayW = ImGui.getIO().displaySizeX
        val displayH = ImGui.getIO().displaySizeY

        ImGui.setNextWindowPos(
            displayW * 0.5f,
            displayH * 0.5f,
            ImGuiCond.Appearing,
            0.5f, 0.5f
        )
        ImGui.setNextWindowSize(480f, 0f, ImGuiCond.Appearing)

        val flags = ImGuiWindowFlags.AlwaysAutoResize or
                    ImGuiWindowFlags.NoMove            or
                    ImGuiWindowFlags.NoCollapse

        if (ImGui.beginPopupModal(MODAL_ID, flags)) {
            session.uiTheme.withFont(UITheme.FontLevel.H2) {
                ImGui.textColored(0.2f, 0.8f, 1.0f, 1.0f, "${Icons.DOWNLOAD} New Version Available!")
            }
            ImGui.separator()
            ImGui.spacing()

            ImGui.textWrapped("A newer version of Liquid LSD is available on GitHub.")
            ImGui.spacing()

            session.uiTheme.withFont(UITheme.FontLevel.CODE) {
                ImGui.text("Current Version: ")
                ImGui.sameLine()
                ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, currentVersion)

                ImGui.text("Latest Version:  ")
                ImGui.sameLine()
                ImGui.textColored(0.35f, 0.9f, 0.45f, 1.0f, release.tagName)
            }

            if (release.name.isNotBlank() && release.name != release.tagName) {
                ImGui.spacing()
                session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                    ImGui.textColored(0.8f, 0.8f, 0.8f, 0.85f, "Release: ${release.name}")
                }
            }

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            // ── Buttons ──
            ImGui.pushStyleColor(ImGuiCol.Button, 0.15f, 0.60f, 0.30f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.22f, 0.75f, 0.38f, 1.0f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.10f, 0.50f, 0.25f, 1.0f)

            if (ImGui.button("${Icons.DOWNLOAD} Download Update", 160f, 32f)) {
                DocManager.openUrl(release.htmlUrl)
                latestRelease = null
                ImGui.closeCurrentPopup()
            }
            ImGui.popStyleColor(3)

            ImGui.sameLine()
            if (ImGui.button("Remind Later", 120f, 32f)) {
                latestRelease = null
                ImGui.closeCurrentPopup()
            }

            ImGui.sameLine()
            if (ImGui.button("Skip Version", 120f, 32f)) {
                session.uiTheme.ignoredUpdateVersion = release.tagName
                session.uiTheme.saveSettings()
                latestRelease = null
                ImGui.closeCurrentPopup()
            }
            itemTooltip("Don't prompt again for ${release.tagName}. You can still check manually anytime.")

            ImGui.endPopup()
        }
    }
}
