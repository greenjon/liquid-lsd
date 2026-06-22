package llm.slop.spirals.patches

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.spirals.models.*
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.Mixer
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue

object PatchManager {
    private val logger = KotlinLogging.logger {}

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    val globalPatchQueue = ConcurrentLinkedQueue<GlobalPatchDto>()
    val deckAPatchQueue = ConcurrentLinkedQueue<DeckPatchDto>()
    val deckBPatchQueue = ConcurrentLinkedQueue<DeckPatchDto>()

    var activePresetA: String? = null
    var activePresetB: String? = null
    var cachedDtoA: DeckPatchDto? = null
    var cachedDtoB: DeckPatchDto? = null

    fun isDeckDirty(deck: Deck, isDeckA: Boolean): Boolean {
        val cached = if (isDeckA) cachedDtoA else cachedDtoB
        if (cached == null) return false
        val current = deck.toDto(cached.name)
        return current != cached
    }

    fun loadGlobalPatchAsync(file: File) {
        CompletableFuture.runAsync {
            try {
                logger.info { "Loading global patch from ${file.absolutePath} in background..." }
                val content = file.readText()
                val dto = json.decodeFromString<GlobalPatchDto>(content)
                globalPatchQueue.offer(dto)
                logger.info { "Global patch loaded from file and queued for main thread apply" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load global patch from ${file.absolutePath}" }
            }
        }
    }

    fun loadDeckPresetAsync(file: File, isDeckA: Boolean) {
        CompletableFuture.runAsync {
            try {
                logger.info { "Loading deck preset from ${file.absolutePath} in background..." }
                val content = file.readText()
                val dto = json.decodeFromString<DeckPatchDto>(content)
                if (isDeckA) {
                    deckAPatchQueue.offer(dto)
                } else {
                    deckBPatchQueue.offer(dto)
                }
                logger.info { "Deck preset loaded and queued for main thread swap" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load deck preset from ${file.absolutePath}" }
            }
        }
    }

    fun saveGlobalPatchAsync(file: File, mixer: Mixer, name: String) {
        // Capture states on the main thread to ensure we don't read changing values from other threads
        val dto = mixer.toDto(name)
        CompletableFuture.runAsync {
            try {
                logger.info { "Saving global patch to ${file.absolutePath} in background..." }
                val content = json.encodeToString(dto)
                file.parentFile?.mkdirs()
                file.writeText(content)
                logger.info { "Global patch saved to file successfully" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save global patch to ${file.absolutePath}" }
            }
        }
    }

    fun saveDeckPresetAsync(file: File, deck: Deck, name: String) {
        // Capture deck state on the main thread
        val dto = deck.toDto(name)
        CompletableFuture.runAsync {
            try {
                logger.info { "Saving deck preset to ${file.absolutePath} in background..." }
                val content = json.encodeToString(dto)
                file.parentFile?.mkdirs()
                file.writeText(content)
                logger.info { "Deck preset saved to file successfully" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save deck preset to ${file.absolutePath}" }
            }
        }
    }

    fun applyPendingPatches(mixer: Mixer) {
        // Poll global patch queue
        var globalDto = globalPatchQueue.poll()
        while (globalDto != null) {
            try {
                mixer.applyDto(globalDto)
                logger.info { "Successfully applied global patch: ${globalDto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying global patch" }
            }
            globalDto = globalPatchQueue.poll()
        }

        // Poll deck A patch queue
        var deckADto = deckAPatchQueue.poll()
        while (deckADto != null) {
            try {
                mixer.deckA.applyDto(deckADto)
                activePresetA = deckADto.name
                cachedDtoA = deckADto
                logger.info { "Successfully applied Deck A preset: ${deckADto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying Deck A preset" }
            }
            deckADto = deckAPatchQueue.poll()
        }

        // Poll deck B patch queue
        var deckBDto = deckBPatchQueue.poll()
        while (deckBDto != null) {
            try {
                mixer.deckB.applyDto(deckBDto)
                activePresetB = deckBDto.name
                cachedDtoB = deckBDto
                logger.info { "Successfully applied Deck B preset: ${deckBDto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying Deck B preset" }
            }
            deckBDto = deckBPatchQueue.poll()
        }
    }
}
