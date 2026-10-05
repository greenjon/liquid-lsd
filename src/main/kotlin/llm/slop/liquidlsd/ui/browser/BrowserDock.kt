package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.LibraryNavigation
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.LibraryPanel.LibraryViewMode
import llm.slop.liquidlsd.ui.ParametersState
import llm.slop.liquidlsd.ui.TangoPalette
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.shortcuts.ShortcutManager

/**
 * The browser as one reusable block: tab strip + action toolbar ([drawHeader]), the pane ([drawBody]),
 * and the keyboard shortcuts and popups that belong to it ([drawShortcuts], [drawPopups]). The Library
 * window draws it unbound; see `.planning/library-browser-unification-plan.md`.
 */
object BrowserDock {
    /** Tab strip `Sources | FX | Transitions | Macros` followed by the centred action toolbar. Leaves the cursor after the toolbar. */
    fun drawHeader(session: SessionContext, mixer: Mixer, parametersState: ParametersState, width: Float, btnH: Float, yOffset: Float) {
        ImGui.setCursorPosX(8f)
        ImGui.setCursorPosY(yOffset)
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            val activeCol = TangoPalette.MODE_ACTIVE.u32()
            val inactiveCol = TangoPalette.MODE_INACTIVE.u32()
            val activeTextCol = TangoPalette.MODE_ACTIVE_TEXT.u32()
            val inactiveTextCol = ImGui.getColorU32(ImGuiCol.Text)
            val tabs = listOf(
                Triple(LibraryViewMode.PRESETS, "Sources##mode_presets", 64f),
                Triple(LibraryViewMode.FX, "FX##mode_fx", 54f),
                Triple(LibraryViewMode.TRANS, "Transitions##mode_trans", 82f),
                Triple(LibraryViewMode.MAPS, "Macros##mode_maps", 54f)
            )
            for ((i, tab) in tabs.withIndex()) {
                val (mode, label, w) = tab
                if (i > 0) ImGui.sameLine(0f, 2f)
                val active = LibraryPanel.viewMode == mode
                ImGui.pushStyleColor(ImGuiCol.Button, if (active) activeCol else inactiveCol)
                ImGui.pushStyleColor(ImGuiCol.Text, if (active) activeTextCol else inactiveTextCol)
                if (ImGui.button(label, w, btnH)) LibraryNavigation.setViewMode(mode)
                ImGui.popStyleColor(2)
            }
        }

        val totalToolbarW = BrowserActionToolbar.calculateToolbarWidth(btnH)
        val windowBtnW = (btnH * 1.15f).coerceIn(20f, 32f)
        val targetCenterX = ((width - totalToolbarW) * 0.5f).coerceIn(120f, (width - totalToolbarW - windowBtnW - 8f).coerceAtLeast(120f))
        ImGui.setCursorPosX(targetCenterX)
        ImGui.setCursorPosY(yOffset)
        BrowserActionToolbar.draw(
            session = session,
            mixer = mixer,
            parametersState = parametersState,
            selectedFile = LibraryPanel.getActiveSelectedFile(session),
            source = LibraryPanel.activeSelectionSource,
            btnHeight = btnH
        )
    }

    /** The pane (or the Macros list) of the selected tab, filling the remaining space. */
    fun drawBody(session: SessionContext, mixer: Mixer, parametersState: ParametersState) {
        val contentH = (ImGui.getContentRegionAvailY() - 4f).coerceAtLeast(1f)
        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(80f)
        val outerFlags = ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse

        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 6f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 6f, 6f)
        ImGui.pushStyleColor(ImGuiCol.ChildBg, TangoPalette.PANEL_BG.u32())
        ImGui.pushStyleColor(ImGuiCol.Border, TangoPalette.PANEL_BORDER.u32())

        val unifiedKind = when (LibraryPanel.viewMode) {
            LibraryViewMode.PRESETS -> BrowseKind.SRC
            LibraryViewMode.FX -> BrowseKind.FX
            LibraryViewMode.TRANS -> BrowseKind.TRANS
            LibraryViewMode.MAPS -> null
        }
        if (LibraryPanel.viewMode == LibraryViewMode.MAPS) {
            // Banks and Pages take the whole panel: no playlists or queues.
            ImGui.beginChild("LibraryMaps", availW, contentH, true, outerFlags)
            ImGui.setScrollX(0f)
            MapsBrowserPanel.draw(session, mixer)
            ImGui.endChild()
        } else if (unifiedKind != null) {
            ImGui.beginChild("LibraryUnified", availW, contentH, false, outerFlags)
            BrowserPane.draw(session, mixer, parametersState, unifiedKind)
            ImGui.endChild()
        }

        ImGui.popStyleColor(2)
        ImGui.popStyleVar(2)
    }

    /** Q / Shift+Q enqueue, Up/Down stepping, and (Transitions) Enter to apply. Dead while a text field has the keyboard. */
    fun drawShortcuts(session: SessionContext, mixer: Mixer) {
        val io = ImGui.getIO()
        if (io.wantTextInput) return
        val activeFile = LibraryPanel.getActiveSelectedFile(session)
        val isQueueAB = ShortcutManager.isTriggered("library.queue_ab")
        val isNavUp = ShortcutManager.isTriggered("library.navigate")
        val isNavDown = !io.keyCtrl && !io.keyAlt && !io.keySuper && !io.keyShift && ImGui.isKeyPressed(ImGuiKey.DownArrow, false)

        if (LibraryPanel.viewMode == LibraryViewMode.TRANS) {
            // Transitions: Q enqueues, Enter applies to the mixer, Up/Down step.
            val isEnter = ImGui.isKeyPressed(ImGuiKey.Enter, false) || ImGui.isKeyPressed(ImGuiKey.KeypadEnter, false)
            if (isEnter && activeFile != null) {
                TransitionQueueManager.applyTransitionItem(activeFile, mixer)
                LibraryPanel.shouldReclaimFocus = true
            } else if (isQueueAB && activeFile != null) {
                TransitionQueueManager.appendToQueue(activeFile)
                LibraryPanel.shouldReclaimFocus = true
            } else if (isNavUp) {
                LibraryPanel.navigateSelection(-1, session, mixer)
            } else if (isNavDown) {
                LibraryPanel.navigateSelection(1, session, mixer)
            }
        } else {
            val isQueueBG = ShortcutManager.isTriggered("library.queue_bg")
            if (isQueueBG && LibraryNavigation.enqueue(session, bg = true)) {
                LibraryPanel.shouldReclaimFocus = true
            } else if (isQueueAB && LibraryNavigation.enqueue(session, bg = false)) {
                LibraryPanel.shouldReclaimFocus = true
            } else if (isNavUp) {
                LibraryPanel.navigateSelection(-1, session, mixer)
            } else if (isNavDown) {
                LibraryPanel.navigateSelection(1, session, mixer)
            }
        }
    }

    /**
     * Rename / new playlist / export-queue popups. Delete's trigger+draw is global (see
     * [BrowserPopupHandler.drawDeleteAssetConfirmationPopup]).
     */
    fun drawPopups(session: SessionContext) {
        if (BrowserPopupHandler.pendingOpenRenamePopup) {
            ImGui.openPopup("RenameAssetPopup")
            BrowserPopupHandler.pendingOpenRenamePopup = false
        }
        if (BrowserPopupHandler.pendingOpenNewPlaylistPopup) {
            ImGui.openPopup("NewPlaylistPopup")
            BrowserPopupHandler.pendingOpenNewPlaylistPopup = false
        }
        if (BrowserPopupHandler.pendingOpenExportQueuePopup) {
            ImGui.openPopup(if (LibraryPanel.viewMode == LibraryViewMode.TRANS) "ExportTransQueuePopup" else "ExportQueuePopup")
            BrowserPopupHandler.pendingOpenExportQueuePopup = false
        }
        if (BrowserPopupHandler.pendingOpenExportFxQueuePopup) {
            ImGui.openPopup("ExportFxQueuePopup")
            BrowserPopupHandler.pendingOpenExportFxQueuePopup = false
        }
        if (BrowserPopupHandler.pendingOpenExportFxBgQueuePopup) {
            ImGui.openPopup("ExportFxBgQueuePopup")
            BrowserPopupHandler.pendingOpenExportFxBgQueuePopup = false
        }

        BrowserPopupHandler.drawRenameAssetPopup()
        BrowserPopupHandler.drawNewPlaylistPopup()
        BrowserPopupHandler.drawExportQueuePopup(session)
        BrowserPopupHandler.drawExportBgQueuePopup()
        BrowserPopupHandler.drawExportTransQueuePopup()
        BrowserPopupHandler.drawExportFxQueuePopup()
        BrowserPopupHandler.drawExportFxBgQueuePopup()
    }
}
