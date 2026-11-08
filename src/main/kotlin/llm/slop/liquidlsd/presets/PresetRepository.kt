package llm.slop.liquidlsd.presets

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.*
import llm.slop.liquidlsd.notes.NotesManager
import llm.slop.liquidlsd.rendering.Deck
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.CompletableFuture

object PresetRepository {
    private val logger = KotlinLogging.logger {}

    /** Logs a failed save and tells the user why ("No space left on device", "the file is read-only"). */
    private fun saveFailed(what: String, file: File, e: Exception) {
        logger.error(e) { "Failed to save $what to ${file.absolutePath}" }
        llm.slop.liquidlsd.ui.ToastOverlay.show("Could not save $what ${file.name}: ${failureReason(e)}", 8000L)
    }

    /** The short reason for a failed file write, without the path the exception repeats. */
    internal fun failureReason(e: Exception): String = when (e) {
        is java.nio.file.AccessDeniedException -> if (e.reason == "file is read-only") "the file is read-only" else "permission denied"
        is java.nio.file.FileSystemException -> e.reason ?: "see log"
        is java.io.FileNotFoundException -> e.message?.substringAfterLast(" (", "")?.removeSuffix(")")?.ifBlank { null } ?: "see log"
        else -> e.message?.ifBlank { null } ?: "see log"
    }

    /** [onFailed] runs on the IO thread after a failed write (the message is already shown). */
    fun saveDeckPresetAsync(file: File, deck: Deck, name: String, tags: List<String> = emptyList(), deckIndex: Int = -1, onFailed: (() -> Unit)? = null) {
        if (deck.source is llm.slop.liquidlsd.rendering.ExternalVideoSource) {
            logger.warn { "Refusing to save deck preset: source is an external video stream (${deck.source.displayName})" }
            return
        }
        val deckLabel = when (deckIndex) {
            0 -> "Deck A"
            1 -> "Deck B"
            2 -> "Deck BG"
            3 -> "Deck PV"
            else -> "Deck"
        }
        val rawDto = deck.toDto(name, tags)
        val canonicalBankId = llm.slop.liquidlsd.macro.MacroEngine.canonicalIdForDeckLabel(deckLabel)
        val deckMacroBank = llm.slop.liquidlsd.macro.MacroEngine.getBank(canonicalBankId)?.let {
            llm.slop.liquidlsd.macro.MacroBankSerializer.snapshotForPreset(it)
        }
        val dto = NotesManager.syncToDto(deckLabel, rawDto.copy(macroBank = deckMacroBank))

        if (deckIndex in 0..3) {
            PresetManager.deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.SAVING))
            PresetManager.pendingSaves[deckIndex].getAndSet(null)?.cancel(false)
        }

        val future = CompletableFuture.runAsync({
            llm.slop.liquidlsd.audio.AudioEngine.presetIOInFlight.compareAndSet(false, true)
            try {
                logger.info { "Saving deck preset to ${file.absolutePath} in background..." }
                val content = PresetManager.json.encodeToString(dto)
                file.parentFile?.mkdirs()
                llm.slop.liquidlsd.utils.AtomicFile.write(file, content)
                llm.slop.liquidlsd.ui.FileSystemManager.clearScanCache() // the Library shows the new file at once
                when (deckIndex) {
                    0 -> PresetManager.activePresetMtimeA = file.lastModified().takeIf { it > 0L }
                    1 -> PresetManager.activePresetMtimeB = file.lastModified().takeIf { it > 0L }
                    2 -> PresetManager.activePresetMtimeBG = file.lastModified().takeIf { it > 0L }
                    3 -> PresetManager.activePresetMtimePV = file.lastModified().takeIf { it > 0L }
                }
                logger.info { "Deck preset saved to file successfully" }
                if (deckIndex in 0..3) {
                    PresetManager.deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.IDLE))
                }
            } catch (e: Exception) {
                saveFailed("preset", file, e)
                onFailed?.invoke()
                if (deckIndex in 0..3) {
                    PresetManager.deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.ERROR, e.message ?: "Unknown error"))
                }
            } finally {
                llm.slop.liquidlsd.audio.AudioEngine.presetIOInFlight.compareAndSet(true, false)
            }
        }, PresetManager.presetIoExecutor)

        if (deckIndex in 0..3) {
            PresetManager.pendingSaves[deckIndex].set(future)
        }
    }

    fun saveFxPresetAsync(file: File, name: String, slotDto: FXSlotDto, tags: List<String> = emptyList()) {
        CompletableFuture.runAsync({
            try {
                logger.info { "Saving FX preset to ${file.absolutePath}..." }
                val dto = FXPresetDto(name = name, tags = tags, slot = slotDto)
                file.parentFile?.mkdirs()
                llm.slop.liquidlsd.utils.AtomicFile.write(file, PresetManager.json.encodeToString(dto))
                llm.slop.liquidlsd.ui.FileSystemManager.clearScanCache() // the Library shows the new file at once
                logger.info { "FX preset saved successfully to ${file.name}" }
            } catch (e: Exception) {
                saveFailed("FX preset", file, e)
            }
        }, PresetManager.presetIoExecutor)
    }

    fun loadFxPresetAsync(file: File): CompletableFuture<FXPresetDto> {
        return CompletableFuture.supplyAsync({
            if (!file.exists()) throw java.io.FileNotFoundException(file.absolutePath)
            val content = file.readText()
            PresetManager.json.decodeFromString<FXPresetDto>(content)
        }, PresetManager.presetIoExecutor)
    }

    /** Writes [chainDto] to [file]; completes with false (after telling the user) when the write failed. */
    fun saveFxChainAsync(file: File, name: String, chainDto: FXChainDto, tags: List<String> = emptyList()): CompletableFuture<Boolean> =
        CompletableFuture.supplyAsync({
            try {
                logger.info { "Saving FX chain to ${file.absolutePath}..." }
                val dto = chainDto.copy(name = name, tags = tags)
                file.parentFile?.mkdirs()
                llm.slop.liquidlsd.utils.AtomicFile.write(file, PresetManager.json.encodeToString(dto))
                llm.slop.liquidlsd.ui.FileSystemManager.clearScanCache() // the Library shows the new file at once
                logger.info { "FX chain saved successfully to ${file.name}" }
                true
            } catch (e: Exception) {
                saveFailed("FX chain", file, e)
                false
            }
        }, PresetManager.presetIoExecutor)

    fun loadFxChainAsync(file: File): CompletableFuture<FXChainDto> {
        return CompletableFuture.supplyAsync({
            if (!file.exists()) throw java.io.FileNotFoundException(file.absolutePath)
            val content = file.readText()
            PresetManager.json.decodeFromString<FXChainDto>(content)
        }, PresetManager.presetIoExecutor)
    }

    fun saveTransitionPresetAsync(file: File, name: String, slotDto: FXSlotDto, tags: List<String> = emptyList()) {
        CompletableFuture.runAsync({
            try {
                logger.info { "Saving Transition preset to ${file.absolutePath}..." }
                val dto = TransitionPresetDto(name = name, tags = tags, slot = slotDto)
                file.parentFile?.mkdirs()
                llm.slop.liquidlsd.utils.AtomicFile.write(file, PresetManager.json.encodeToString(dto))
                llm.slop.liquidlsd.ui.FileSystemManager.clearScanCache() // the Library shows the new file at once
                logger.info { "Transition preset saved successfully to ${file.name}" }
            } catch (e: Exception) {
                saveFailed("transition", file, e)
            }
        }, PresetManager.presetIoExecutor)
    }

    fun saveTransitionPlaylistAsync(file: File, playlist: TransitionPlaylistDto) {
        CompletableFuture.runAsync({
            try {
                logger.info { "Saving Transition playlist to ${file.absolutePath}..." }
                file.parentFile?.mkdirs()
                llm.slop.liquidlsd.utils.AtomicFile.write(file, PresetManager.json.encodeToString(playlist))
                llm.slop.liquidlsd.ui.FileSystemManager.clearScanCache() // the Library shows the new file at once
                logger.info { "Transition playlist saved successfully to ${file.name}" }
            } catch (e: Exception) {
                saveFailed("transition playlist", file, e)
            }
        }, PresetManager.presetIoExecutor)
    }

    fun loadTransitionPlaylistAsync(file: File): CompletableFuture<TransitionPlaylistDto> {
        return CompletableFuture.supplyAsync({
            if (!file.exists()) throw java.io.FileNotFoundException(file.absolutePath)
            val content = file.readText()
            PresetManager.json.decodeFromString<TransitionPlaylistDto>(content)
        }, PresetManager.presetIoExecutor)
    }

    fun saveFxPlaylistAsync(file: File, playlist: FXPlaylistDto) {
        CompletableFuture.runAsync({
            try {
                logger.info { "Saving FX playlist to ${file.absolutePath}..." }
                file.parentFile?.mkdirs()
                llm.slop.liquidlsd.utils.AtomicFile.write(file, PresetManager.json.encodeToString(playlist))
                llm.slop.liquidlsd.ui.FileSystemManager.clearScanCache() // the Library shows the new file at once
                logger.info { "FX playlist saved successfully to ${file.name}" }
            } catch (e: Exception) {
                saveFailed("FX playlist", file, e)
            }
        }, PresetManager.presetIoExecutor)
    }

    fun loadFxPlaylistAsync(file: File): CompletableFuture<FXPlaylistDto> {
        return CompletableFuture.supplyAsync({
            if (!file.exists()) throw java.io.FileNotFoundException(file.absolutePath)
            val content = file.readText()
            PresetManager.json.decodeFromString<FXPlaylistDto>(content)
        }, PresetManager.presetIoExecutor)
    }
}
