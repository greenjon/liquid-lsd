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

    fun loadDeckPresetAsync(
        file: File,
        isDeckA: Boolean = false,
        isDeckBG: Boolean = false,
        isDeckPV: Boolean = false,
        isManual: Boolean = true
    ) {
        val deckIndex = when {
            isDeckA -> 0
            isDeckBG -> 2
            isDeckPV -> 3
            else -> 1 // Deck B
        }
        PresetManager.deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.LOADING))
        val fileMtime = file.lastModified().takeIf { it > 0L }
        CompletableFuture.runAsync({
            llm.slop.liquidlsd.audio.AudioEngine.presetIOInFlight.compareAndSet(false, true)
            try {
                logger.info { "Loading deck preset from ${file.absolutePath} in background..." }
                if (!file.exists()) throw java.io.FileNotFoundException(file.absolutePath)
                
                val content = file.readText()
                val rawDto = PresetManager.json.decodeFromString<DeckPresetDto>(content)
                val namedDto = rawDto.copy(name = file.nameWithoutExtension)
                val (sanitizedDto, wasMigrated) = PresetMigrator.sanitizePresetDto(namedDto)

                if (wasMigrated && file.canWrite()) {
                    try {
                        file.writeText(PresetManager.json.encodeToString(sanitizedDto))
                        logger.info { "Auto-healed and migrated preset '${file.name}' to latest schema" }
                    } catch (e: Exception) {
                        logger.warn(e) { "Could not auto-save migrated preset '${file.name}'" }
                    }
                }

                val pending = PresetManager.PendingDeckLoad(sanitizedDto, isManual)
                when {
                    isDeckA -> PresetManager.deckAPresetQueue.offer(pending)
                    isDeckBG -> PresetManager.deckBGPresetQueue.offer(pending)
                    isDeckPV -> PresetManager.deckPVPresetQueue.offer(pending)
                    else -> PresetManager.deckBPresetQueue.offer(pending)
                }
                when {
                    isDeckA -> PresetManager.activePresetMtimeA = fileMtime
                    isDeckBG -> PresetManager.activePresetMtimeBG = fileMtime
                    isDeckPV -> PresetManager.activePresetMtimePV = fileMtime
                    else    -> PresetManager.activePresetMtimeB = fileMtime
                }
                logger.info { "Deck preset loaded and queued for main thread swap" }
                PresetManager.deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.IDLE))
            } catch (e: Exception) {
                logger.error(e) { "Failed to load deck preset from ${file.absolutePath}" }
                PresetManager.deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.ERROR, e.message ?: "Unknown error"))
            } finally {
                llm.slop.liquidlsd.audio.AudioEngine.presetIOInFlight.compareAndSet(true, false)
            }
        }, PresetManager.presetIoExecutor)
    }

    fun saveDeckPresetAsync(file: File, deck: Deck, name: String, tags: List<String> = emptyList(), deckIndex: Int = -1) {
        val deckLabel = when (deckIndex) {
            0 -> "Deck A"
            1 -> "Deck B"
            2 -> "Deck BG"
            3 -> "Deck PV"
            else -> "Deck"
        }
        val dto = NotesManager.syncToDto(deckLabel, deck.toDto(name, tags))

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
                file.writeText(content)
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
                logger.error(e) { "Failed to save deck preset to ${file.absolutePath}" }
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
                file.writeText(PresetManager.json.encodeToString(dto))
                logger.info { "FX preset saved successfully to ${file.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save FX preset to ${file.absolutePath}" }
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

    fun saveFxChainAsync(file: File, name: String, chainDto: FXChainDto, tags: List<String> = emptyList()) {
        CompletableFuture.runAsync({
            try {
                logger.info { "Saving FX chain to ${file.absolutePath}..." }
                val dto = chainDto.copy(name = name, tags = tags)
                file.parentFile?.mkdirs()
                file.writeText(PresetManager.json.encodeToString(dto))
                logger.info { "FX chain saved successfully to ${file.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save FX chain to ${file.absolutePath}" }
            }
        }, PresetManager.presetIoExecutor)
    }

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
                file.writeText(PresetManager.json.encodeToString(dto))
                logger.info { "Transition preset saved successfully to ${file.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save Transition preset to ${file.absolutePath}" }
            }
        }, PresetManager.presetIoExecutor)
    }

    fun loadTransitionPresetAsync(file: File): CompletableFuture<TransitionPresetDto> {
        return CompletableFuture.supplyAsync({
            if (!file.exists()) throw java.io.FileNotFoundException(file.absolutePath)
            val content = file.readText()
            PresetManager.json.decodeFromString<TransitionPresetDto>(content)
        }, PresetManager.presetIoExecutor)
    }

    fun saveTransitionPlaylistAsync(file: File, playlist: TransitionPlaylistDto) {
        CompletableFuture.runAsync({
            try {
                logger.info { "Saving Transition playlist to ${file.absolutePath}..." }
                file.parentFile?.mkdirs()
                file.writeText(PresetManager.json.encodeToString(playlist))
                logger.info { "Transition playlist saved successfully to ${file.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save Transition playlist to ${file.absolutePath}" }
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
}
