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
import llm.slop.liquidlsd.ui.itemTooltip
import llm.slop.liquidlsd.ui.shortcuts.ShortcutManager

/**
 * The browser as one reusable block: tab strip + action toolbar ([drawHeader]), the pane ([drawBody]),
 * and the keyboard shortcuts and popups that belong to it ([drawShortcuts], [drawPopups]). The Library
 * window draws it unbound; see `.planning/library-browser-unification-plan.md`.
 */
object BrowserDock {
    /** What the dock applies to while it is opened from an Edit row: the row's [target], a [label] for the chip, and row-specific buttons ([actions], e.g. Save, Clear Slot). */
    class DockBinding(
        val target: ApplyTarget,
        val label: String,
        val actions: (() -> Unit)? = null,
        /** The row's colour: tints the chip and the pane border while bound. */
        val accent: FloatArray? = null,
        /** Non-null when the dock owns the selection (Library dock): the chip shows a close button that runs it. */
        val onClose: (() -> Unit)? = null
    )

    private var syncedKey: String? = null

    /** The Library window is drawing the dock again, so the next Edit row re-selects its tab. */
    fun libraryShown() { syncedKey = null }

    private fun modeFor(kind: BrowseKind) = when (kind) {
        BrowseKind.SRC -> LibraryViewMode.PRESETS
        BrowseKind.FX -> LibraryViewMode.FX
        BrowseKind.TRANS -> LibraryViewMode.TRANS
    }

    /** Opens on the tab of the row's kind whenever the row (or its slot) changes; a tab the user picks afterwards sticks until then. */
    private fun syncTab(binding: DockBinding) {
        if (binding.target.contextKey == syncedKey) return
        syncedKey = binding.target.contextKey
        LibraryNavigation.setViewMode(modeFor(binding.target.kind))
    }

    private const val TABS_W = 64f + 54f + 82f + 62f + 54f + 4 * 2f

    /** Tab strip `Sources | FX | Transitions | Queues | Macros` followed by the centred action toolbar. Leaves the cursor after the toolbar. */
    fun drawHeader(
        session: SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        width: Float,
        btnH: Float,
        yOffset: Float,
        x0: Float = 8f,
        binding: DockBinding? = null
    ) {
        ImGui.setCursorPosX(x0)
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
                Triple(LibraryViewMode.QUEUES, "Queues##mode_queues", 62f),
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
        val tabsEndX = x0 + TABS_W + 8f
        // With a chip on the line the toolbar sits right after the tabs; without one it is centred.
        val targetCenterX = if (binding != null) tabsEndX
            else ((width - totalToolbarW) * 0.5f).coerceIn(120f, (width - totalToolbarW - windowBtnW - 8f).coerceAtLeast(120f)).coerceAtLeast(tabsEndX)
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
        if (binding != null) {
            ImGui.sameLine(0f, 14f)
            ImGui.setCursorPosY(yOffset)
            drawChip(session, binding, boundTab(binding), btnH)
        }
    }

    /** True while the selected tab is the kind the target edits; any other tab is the plain Library, with the binding paused. */
    private fun boundTab(binding: DockBinding): Boolean = viewKind() == binding.target.kind

    private fun viewKind(): BrowseKind? = when (LibraryPanel.viewMode) {
        LibraryViewMode.PRESETS -> BrowseKind.SRC
        LibraryViewMode.FX -> BrowseKind.FX
        LibraryViewMode.TRANS -> BrowseKind.TRANS
        LibraryViewMode.MAPS, LibraryViewMode.QUEUES -> null
    }

    /** The chip on the header line: `● Deck A · FX 1  [row buttons]  ✕`. Greyed (no buttons) while a tab of another kind is up, so the binding reads as paused, not gone. */
    private fun drawChip(session: SessionContext, binding: DockBinding, bound: Boolean, btnH: Float) {
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            val dot = if (bound && binding.accent != null) TangoPalette.u32(binding.accent) else ImGui.getColorU32(ImGuiCol.TextDisabled)
            ImGui.alignTextToFramePadding()
            val cx = ImGui.getCursorScreenPosX()
            val cy = ImGui.getCursorScreenPosY() + ImGui.getFrameHeight() * 0.5f
            ImGui.getWindowDrawList().addCircleFilled(cx + 5f, cy, 4f, dot)
            ImGui.dummy(14f, 0f)
            ImGui.sameLine(0f, 0f)
            if (bound) ImGui.text(binding.label) else ImGui.textDisabled("${binding.label} (paused)")
            itemTooltip(if (bound) "A click applies to ${binding.label}. Double-click applies and ends the binding." else "Pick the ${binding.target.kind.name.lowercase()} tab to apply to ${binding.label} again.")
            if (bound) binding.actions?.let { ImGui.sameLine(0f, 10f); it() }
            binding.onClose?.let { close ->
                ImGui.sameLine(0f, 8f)
                if (ImGui.button("${llm.slop.liquidlsd.ui.Icons.X}##dock_unbind", btnH, btnH)) close()
                itemTooltip("Stop applying to ${binding.label} and go back to the plain Library (Esc).")
            }
        }
    }

    /** The pane (or the Macros list) of the selected tab, filling the remaining space. */
    fun drawBody(session: SessionContext, mixer: Mixer, parametersState: ParametersState, binding: DockBinding? = null) {
        if (binding != null) syncTab(binding)
        val kind = when (LibraryPanel.viewMode) {
            LibraryViewMode.PRESETS -> BrowseKind.SRC
            LibraryViewMode.FX -> BrowseKind.FX
            LibraryViewMode.TRANS -> BrowseKind.TRANS
            LibraryViewMode.MAPS, LibraryViewMode.QUEUES -> null
        }
        // Bound only while the selected tab is the kind the row edits; any other tab is the plain Library.
        val bound = binding?.takeIf { it.target.kind == kind }
        val contentH = (ImGui.getContentRegionAvailY() - 4f).coerceAtLeast(1f)
        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(80f)
        val outerFlags = ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse

        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 6f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 6f, 6f)
        ImGui.pushStyleColor(ImGuiCol.ChildBg, TangoPalette.PANEL_BG.u32())
        ImGui.pushStyleColor(ImGuiCol.Border, bound?.accent?.let { TangoPalette.u32(it) } ?: TangoPalette.PANEL_BORDER.u32())

        val unifiedKind = when (LibraryPanel.viewMode) {
            LibraryViewMode.PRESETS -> BrowseKind.SRC
            LibraryViewMode.FX -> BrowseKind.FX
            LibraryViewMode.TRANS -> BrowseKind.TRANS
            LibraryViewMode.MAPS, LibraryViewMode.QUEUES -> null
        }
        if (unifiedKind == null) BrowserPane.noteHosting(null) // the Queues and Maps tabs have no apply-target
        if (LibraryPanel.viewMode == LibraryViewMode.QUEUES) {
            ImGui.beginChild("LibraryQueues", availW, contentH, false, outerFlags)
            QueuesPane.draw(session, mixer)
            ImGui.endChild()
        } else if (LibraryPanel.viewMode == LibraryViewMode.MAPS) {
            // Banks and Pages take the whole panel: no playlists or queues.
            ImGui.beginChild("LibraryMaps", availW, contentH, true, outerFlags)
            ImGui.setScrollX(0f)
            MapsBrowserPanel.draw(session, mixer)
            ImGui.endChild()
        } else if (unifiedKind != null) {
            ImGui.beginChild("LibraryUnified", availW, contentH, false, outerFlags)
            BrowserPane.draw(session, mixer, parametersState, unifiedKind, bound?.target)
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
            ImGui.openPopup("ExportTransQueuePopup") // only the transition queue panel raises this flag; the preset queues open their own
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
