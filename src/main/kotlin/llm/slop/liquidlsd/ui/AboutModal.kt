package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.update.AppVersion
import llm.slop.liquidlsd.update.UpdateChecker
import llm.slop.liquidlsd.update.UpdateCheckResult

/**
 * "About Liquid LSD" modal popup.
 *
 * Displays version details, manual update check, and links to the GitHub repository.
 */
object AboutModal {

    private const val MODAL_ID = "About Liquid LSD###about_modal"
    private var pendingOpen = false

    fun open() {
        pendingOpen = true
    }

    fun draw(session: llm.slop.liquidlsd.SessionContext) {
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
        ImGui.setNextWindowSize(460f, 0f, ImGuiCond.Appearing)

        val flags = ImGuiWindowFlags.AlwaysAutoResize or
                    ImGuiWindowFlags.NoMove            or
                    ImGuiWindowFlags.NoCollapse

        if (ImGui.beginPopupModal(MODAL_ID, flags)) {
            session.uiTheme.withFont(UITheme.FontLevel.H2) {
                ImGui.textColored(0.2f, 0.8f, 1.0f, 1.0f, "${Icons.ACTIVITY} Liquid LSD")
            }
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                ImGui.text("Libre Shader Decks")
            }

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            // ── Version Information ──
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                ImGui.text("Version:")
                ImGui.sameLine()
                session.uiTheme.withFont(UITheme.FontLevel.CODE) {
                    ImGui.textColored(0.35f, 0.85f, 1.0f, 1.0f, AppVersion.CURRENT)
                }
            }

            ImGui.spacing()

            // ── Update Check Controls ──
            val checking = UpdateChecker.isChecking
            val lastRes = UpdateChecker.lastResult

            if (checking) {
                session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                    ImGui.textColored(0.9f, 0.7f, 0.2f, 1.0f, "${Icons.REFRESH} Checking for updates...")
                }
            } else {
                when (lastRes) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                            ImGui.textColored(0.3f, 0.9f, 0.4f, 1.0f, "${Icons.DOWNLOAD} Update available: ${lastRes.latestRelease.tagName}")
                        }
                        ImGui.spacing()
                        ImGui.pushStyleColor(ImGuiCol.Button, 0.15f, 0.60f, 0.30f, 1.0f)
                        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.22f, 0.75f, 0.38f, 1.0f)
                        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.10f, 0.50f, 0.25f, 1.0f)
                        if (ImGui.button("${Icons.DOWNLOAD} View Update", 160f, 28f)) {
                            UpdatePromptModal.request(lastRes.latestRelease, lastRes.currentVersion)
                            ImGui.closeCurrentPopup()
                        }
                        ImGui.popStyleColor(3)
                    }
                    is UpdateCheckResult.UpToDate -> {
                        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                            ImGui.textColored(0.5f, 0.9f, 0.5f, 1.0f, "Liquid LSD is up to date.")
                        }
                        ImGui.spacing()
                        if (ImGui.button("${Icons.REFRESH} Check Again", 140f, 28f)) {
                            UpdateChecker.checkForUpdatesAsync(isManualCheck = true)
                        }
                    }
                    is UpdateCheckResult.Error -> {
                        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                            ImGui.textColored(1.0f, 0.4f, 0.4f, 1.0f, "Update check failed: ${lastRes.message}")
                        }
                        ImGui.spacing()
                        if (ImGui.button("${Icons.REFRESH} Retry Check", 140f, 28f)) {
                            UpdateChecker.checkForUpdatesAsync(isManualCheck = true)
                        }
                    }
                    UpdateCheckResult.Idle, UpdateCheckResult.Checking -> {
                        if (ImGui.button("${Icons.REFRESH} Check for Updates", 170f, 28f)) {
                            UpdateChecker.checkForUpdatesAsync(isManualCheck = true)
                        }
                    }
                }
            }

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            // ── Links & Dismiss ──
            if (ImGui.button("GitHub Repository", 160f, 30f)) {
                DocManager.openUrl("https://github.com/greenjon/liquid-lsd")
            }
            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                ImGui.setTooltip("Open the project repository on GitHub in your default browser.")
            }

            ImGui.sameLine()
            if (ImGui.button("Documentation", 140f, 30f)) {
                DocManager.openDocumentation()
            }

            ImGui.sameLine()
            val availX = ImGui.getContentRegionAvailX()
            val closeBtnW = 80f
            if (availX > closeBtnW) {
                ImGui.setCursorPosX(ImGui.getCursorPosX() + (availX - closeBtnW))
            }
            if (ImGui.button("Close", closeBtnW, 30f)) {
                ImGui.closeCurrentPopup()
            }

            ImGui.endPopup()
        }
    }
}
