package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.presets.TransitionOps
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.isf.ISFAutoBindEngine
import llm.slop.liquidlsd.ui.browser.BrowseKind
import llm.slop.liquidlsd.ui.browser.BrowseSection
import llm.slop.liquidlsd.ui.browser.LibraryFolders
import java.io.File

/** Saving the mixer's current transition as a `.lsdtrans` preset, shared by the Library "+", the Mixer TRANS tab and the unified browser pane. */
object TransitionSave {
    fun requestSaveCurrent(session: SessionContext, mixer: Mixer) {
        val current = mixer.transitionFilter
        if (current == null) {
            ToastOverlay.show("No transition to save")
            return
        }
        val slotDto = FXSlotDto(
            filterId = current.id,
            enabled = current.enabled,
            dryWet = current.dryWet.toDto(),
            parameters = current.parameters.mapValues { p -> p.value.toDto() }
        )
        SavePresetModal.request(
            title = "Save Transition Preset As",
            confirmLabel = "Save",
            defaultName = current.displayName.lowercase().replace(" ", "_"),
            targetDir = LibraryFolders.saveDirFor(BrowseKind.TRANS, BrowseSection.SAVED, TransitionOps.presetSourceFile(mixer)),
            extension = "lsdtrans",
            folderRoot = FileSystemManager.getTransitionsRoot()
        ) { name, tags ->
            val file = File(SavePresetModal.directory, "$name.lsdtrans")
            session.presetRepository.saveTransitionPresetAsync(file, name, slotDto, tags)
            LibraryPanel.refreshAssets()
        }
    }

    /**
     * "Save as Default for <transition>" and "Remove Saved Default" for the transition menus: the transition's parameter
     * values and modulators, keyed by shader content like an effect's default. Removing it brings the shipped response back.
     */
    fun drawDefaultMenuItems(mixer: Mixer) {
        val trans = mixer.transitionFilter ?: return
        if (trans.contentHash == null) return
        ImGui.separator()
        if (ImGui.menuItem("Save as Default for ${trans.displayName}")) {
            ISFAutoBindEngine.saveFilterDefault(trans)
            TransitionOps.invalidateDefault()
        }
        itemTooltip("Picking ${trans.displayName} from now on starts with these parameter values and modulators (MIDI mappings are not saved).\nSaved .lsdtrans presets keep their own.")
        if (ISFAutoBindEngine.hasFilterDefault(trans)) {
            if (ImGui.menuItem("Remove Saved Default for ${trans.displayName}")) {
                ISFAutoBindEngine.deleteFilterDefault(trans)
                TransitionOps.invalidateDefault()
                TransitionOps.setStock(trans.id, undoable = true)
            }
            itemTooltip("${trans.displayName} goes back to its built-in parameters and audio response, and the running transition is reset to them.")
        }
    }
}
