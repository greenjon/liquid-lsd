package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroBankSerializer
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroLearnState
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.AppPreferencesStore
import llm.slop.liquidlsd.ui.MacroUndoTracker
import llm.slop.liquidlsd.ui.PerfPageStore
import llm.slop.liquidlsd.ui.SavePresetModal
import llm.slop.liquidlsd.ui.ToastOverlay
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import llm.slop.liquidlsd.ui.popOpenDropdownFont
import llm.slop.liquidlsd.ui.popOpenDropdownPadding
import llm.slop.liquidlsd.ui.pushOpenDropdownFont
import llm.slop.liquidlsd.ui.pushOpenDropdownPadding
import llm.slop.liquidlsd.ui.selectableRow
import java.io.File

/**
 * Library "Macros" tab: saved macro banks (`.knobpreset.json` files in `library/knobpresets`) and Perform pages,
 * so both are found next to presets instead of through a raw file browser or Preferences.
 * Banks can be saved from, and applied to, a bank; pages can be shown, hidden, copied and deleted here
 * (editing a page's rows stays in Preferences > MIDI Controls).
 */
object MapsBrowserPanel {
    enum class Tab { BANKS, PAGES }

    var tab = Tab.BANKS
    private var selectedBank: File? = null
    private val bankDir get() = File("library/knobpresets")
    private const val SUFFIX = ".knobpreset.json"

    /** A bank a saved file can be saved from or applied to. Deck banks retarget to their deck; FX banks are rewritten from the FX chain and excluded. */
    private data class BankTarget(val label: String, val bankId: String, val deckLabel: String?)
    private val targets = listOf(
        BankTarget("Deck A", MacroEngine.DECK_A, "Deck A"),
        BankTarget("Deck B", MacroEngine.DECK_B, "Deck B"),
        BankTarget("Deck BG", MacroEngine.DECK_BG, "Deck BG"),
        BankTarget("Deck PV", MacroEngine.DECK_PV, "Deck PV"),
        BankTarget("Master", MacroEngine.MASTER, null),
        BankTarget("Transition", MacroEngine.TRANS, null),
        BankTarget("FX Sends", MacroEngine.FX_SENDS, null)
    )

    private var cachedFiles: List<File> = emptyList()
    private var cachedAtMs = 0L

    private fun bankFiles(force: Boolean = false): List<File> {
        val now = System.currentTimeMillis()
        if (force || now - cachedAtMs > 1000L) {
            cachedFiles = bankDir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }?.sortedBy { it.name.lowercase() } ?: emptyList()
            cachedAtMs = now
        }
        return cachedFiles
    }

    fun draw(session: SessionContext, mixer: Mixer) {
        ImGui.alignTextToFramePadding()
        session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.text("Macros") }
        ImGui.sameLine()
        if (ImGui.radioButton("Banks##maps_tab_banks", tab == Tab.BANKS)) tab = Tab.BANKS
        itemTooltip("Saved macro knob banks: the knob layout and targets of a deck, Master, Transition or FX Sends row.")
        ImGui.sameLine()
        if (ImGui.radioButton("Pages##maps_tab_pages", tab == Tab.PAGES)) tab = Tab.PAGES
        itemTooltip("Perform pages: which rows the Perform view shows, and the controller bank that selects them.")
        ImGui.separator()
        ImGui.spacing()
        when (tab) {
            Tab.BANKS -> drawBanks(session, mixer)
            Tab.PAGES -> drawPages(session)
        }
    }

    private fun drawBanks(session: SessionContext, mixer: Mixer) {
        if (ImGui.button("Save bank from...##maps_save_bank")) ImGui.openPopup("maps_save_bank_menu")
        itemTooltip("Saves the knobs of a deck, Master, Transition or FX Sends row as a bank file you can apply later.")
        pushOpenDropdownPadding()
        if (ImGui.beginPopup("maps_save_bank_menu")) {
            pushOpenDropdownFont()
            for (t in targets) {
                if (ImGui.menuItem(t.label)) requestSave(t)
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        ImGui.spacing()

        val files = bankFiles()
        if (files.isEmpty()) {
            session.uiTheme.caption("No saved banks yet. Use Save bank from... to make one.")
            return
        }
        if (ImGui.beginChild("##maps_bank_list", 0f, 0f, false)) {
            files.forEachIndexed { i, file ->
                val name = file.name.removeSuffix(SUFFIX)
                val selected = selectedBank == file
                session.uiTheme.withFont(UITheme.FontLevel.PRESET_NAME) { selectableRow("$name##maps_bank_$i", selected, ImGui.getContentRegionAvailX()) }
                if (ImGui.isItemClicked(0)) {
                    selectedBank = file
                    llm.slop.liquidlsd.ui.LibraryPanel.activeSelectionSource = null
                }
                itemTooltip("Right-click to apply this bank to a row, or delete it.")
                pushOpenDropdownPadding()
                if (ImGui.beginPopupContextItem("maps_bank_ctx_$i")) {
                    pushOpenDropdownFont()
                    ImGui.textDisabled("Apply to:")
                    for (t in targets) {
                        if (ImGui.menuItem(t.label)) apply(file, t, mixer)
                    }
                    ImGui.separator()
                    if (ImGui.menuItem("Delete")) {
                        if (file.delete()) { if (selectedBank == file) selectedBank = null; bankFiles(force = true) }
                        else ToastOverlay.show("Could not delete ${file.name}")
                    }
                    popOpenDropdownFont()
                    ImGui.endPopup()
                }
                popOpenDropdownPadding()
            }
        }
        ImGui.endChild()
    }

    private fun requestSave(t: BankTarget) {
        val bank = MacroEngine.getBank(t.bankId)
        if (bank == null) {
            ToastOverlay.show("${t.label} has no macro bank to save")
            return
        }
        SavePresetModal.request(
            title = "Save ${t.label} Macro Bank As",
            confirmLabel = "Save",
            defaultName = t.bankId,
            targetDir = bankDir.also { it.mkdirs() },
            extension = "knobpreset.json"
        ) { name, _ ->
            try {
                MacroBankSerializer.exportToFile(File(bankDir, "$name$SUFFIX"), bank)
                bankFiles(force = true)
                ToastOverlay.show("Saved ${t.label} bank as $name")
            } catch (e: Exception) {
                ToastOverlay.show("Could not save bank: ${e.message}")
            }
        }
    }

    private fun apply(file: File, t: BankTarget, mixer: Mixer) {
        val bank = MacroEngine.getBank(t.bankId) ?: return
        try {
            if (t.deckLabel != null) MacroUndoTracker.recordBeforeBulkEdit()
            val skipped = MacroBankSerializer.applyFileToBank(file, t.bankId, bank, t.deckLabel, mixer)
            ToastOverlay.show("Applied ${file.name.removeSuffix(SUFFIX)} to ${t.label}" + if (skipped > 0) " ($skipped target(s) skipped: parameter not found)" else "")
        } catch (e: Exception) {
            ToastOverlay.show("Could not apply bank: ${e.message}")
        }
    }

    private fun drawPages(session: SessionContext) {
        val theme = session.uiTheme
        val store = PerfPageStore.default
        theme.caption("Click a page to show it in Perform. Edit rows in Preferences > MIDI Controls.")
        ImGui.spacing()
        if (ImGui.beginChild("##maps_page_list", 0f, 0f, false)) {
            store.all().forEachIndexed { i, page ->
                val source = when (store.sourceOf(page.id)) {
                    PerfPageStore.Source.BUILT_IN -> "built-in"
                    PerfPageStore.Source.USER -> "user"
                    PerfPageStore.Source.USER_OVERRIDE -> "user override"
                    null -> ""
                }
                val hidden = page.id in theme.hiddenPerformPages
                val label = page.name + "  ($source${if (hidden) ", hidden" else ""})"
                val selected = page.id == theme.performancePageId
                theme.withFont(UITheme.FontLevel.PRESET_NAME) { selectableRow("$label##maps_page_$i", selected, ImGui.getContentRegionAvailX()) }
                if (ImGui.isItemClicked(0)) {
                    theme.performancePageId = page.id
                    AppPreferencesStore.savePreferences()
                }
                itemTooltip("Controller bank name: perform.${page.id}. Right-click for options.")
                pushOpenDropdownPadding()
                if (ImGui.beginPopupContextItem("maps_page_ctx_$i")) {
                    pushOpenDropdownFont()
                    if (ImGui.menuItem(if (hidden) "Show in tab strip" else "Hide from tab strip")) {
                        if (theme.setPerformPageHidden(page.id, !hidden)) AppPreferencesStore.savePreferences()
                        else ToastOverlay.show("At least one page must stay in the tab strip")
                    }
                    val src = store.sourceOf(page.id)
                    if (src == PerfPageStore.Source.BUILT_IN && ImGui.menuItem("Copy to user file")) {
                        ToastOverlay.show(store.copyBuiltInToUser(page.id) ?: "Copied to library/perform_pages/${page.id}.json")
                    }
                    if (src != PerfPageStore.Source.BUILT_IN && ImGui.menuItem("Delete user file")) {
                        ToastOverlay.show(if (store.deleteUser(page.id)) "Deleted user page ${page.id}" else "Could not delete ${page.id}")
                    }
                    popOpenDropdownFont()
                    ImGui.endPopup()
                }
                popOpenDropdownPadding()
            }
        }
        ImGui.endChild()
    }
}
