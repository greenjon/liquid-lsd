package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.rendering.Mixer
import java.io.File

/** Saving the mixer's current transition as a `.lsdtrans` preset, shared by the Library "+", the Mixer TRANS tab and the inline picker. */
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
            targetDir = FileSystemManager.getTransitionsRoot(),
            extension = "lsdtrans"
        ) { name, tags ->
            val file = File(FileSystemManager.getTransitionsRoot(), "$name.lsdtrans")
            session.presetRepository.saveTransitionPresetAsync(file, name, slotDto, tags)
            LibraryPanel.refreshAssets()
        }
    }
}
