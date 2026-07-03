package llm.slop.spirals.patches

import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.models.PlaylistDto
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages the volatile RAM Play Queue (Phase 1).
 */
object PlayQueueManager {
    private val logger = KotlinLogging.logger {}
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    val queue = CopyOnWriteArrayList<File>()
    
    @Volatile
    var isAutoVJEnabled = false

    @Volatile
    var activeIndex = -1
        private set

    fun appendPlaylistToQueue(playlistFile: File) {
        try {
            val content = playlistFile.readText()
            val dto = json.decodeFromString<PlaylistDto>(content)
            dto.items.forEach { itemName ->
                var deckFile = File("presets/decks/$itemName")
                if (!deckFile.exists()) {
                    val possible = listOf(itemName, "$itemName.lsd", "$itemName.json")
                    var found = false
                    for (p in possible) {
                        val f = File("presets/decks/$p")
                        if (f.exists()) {
                            appendToQueue(f)
                            found = true
                            break
                        }
                    }
                    if (!found) logger.warn { "Queue playlist item not found: $itemName" }
                } else {
                    appendToQueue(deckFile)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to append playlist to queue: ${playlistFile.absolutePath}" }
        }
    }

    fun appendToQueue(file: File) {
        queue.add(file)
        logger.info { "Appended to queue: ${file.name}" }
    }

    fun removeFromQueue(index: Int) {
        if (index in queue.indices) {
            val removed = queue.removeAt(index)
            logger.info { "Removed from queue: ${removed.name}" }
            if (index <= activeIndex) {
                activeIndex--
            }
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        if (from in queue.indices && to in queue.indices) {
            val item = queue.removeAt(from)
            queue.add(to, item)
            
            // Adjust activeIndex if it was moved
            if (activeIndex == from) {
                activeIndex = to
            } else if (from < activeIndex && to >= activeIndex) {
                activeIndex--
            } else if (from > activeIndex && to <= activeIndex) {
                activeIndex++
            }
        }
    }

    fun triggerNext(mixer: Mixer) {
        if (queue.isEmpty()) return
        
        val nextIndex = activeIndex + 1
        if (nextIndex >= queue.size) {
            logger.info { "End of queue reached." }
            return
        }
        
        activeIndex = nextIndex
        val file = queue[activeIndex]
        
        // Determine which deck is inactive
        // crossfade 0.0 = Deck A, 1.0 = Deck B
        val targetIsA = mixer.crossfade.value > 0.5f
        
        logger.info { "Triggering next: ${file.name} to Deck ${if (targetIsA) "A" else "B"}" }
        
        PatchManager.loadDeckPresetAsync(file, targetIsA)
        
        // Start auto-fade to the target deck
        mixer.targetCrossfade = if (targetIsA) 0.0f else 1.0f
        mixer.isAutoFading = true
    }
    
    fun clearQueue() {
        queue.clear()
        activeIndex = -1
    }
}
