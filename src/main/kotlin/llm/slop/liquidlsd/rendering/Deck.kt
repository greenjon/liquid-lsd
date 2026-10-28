package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.models.FXPresetDto
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ParameterOwner
import kotlin.math.roundToInt

/**
 * Represents a single visual rendering chain (Deck): a visual source, its 2D framing and its own FX chain,
 * each rendered into the deck's offscreen FBOs.
 */
class Deck(
    initialSource: VisualSource,
    var width: Int = 1920,
    var height: Int = 1080,
    var isEmpty: Boolean = true
) : ParameterOwner {

    /**
     * The deck's own instance of its visual source (a clone of a [VisualSourceRegistry] master). Replacing it
     * releases the old instance's GPU resources; a clone can still be put back (undo) and re-creates them lazily.
     */
    var source: VisualSource = initialSource
        set(value) {
            val old = field
            field = value
            if (old !== value) old.dispose()
            llm.slop.liquidlsd.midi.MidiMappingManager.invalidateBindings()
            llm.slop.liquidlsd.parameters.ParameterResolver.clearCache()
        }

    // FBO for rendering the clean visual source output
    var cleanFBO = FBO(width, height)

    // Per-deck dedicated FX chain (3 serial slots, Super Knob, dry/wet, bypass)
    val fxChain = FxChain("Deck FX")

    // Inner scratch pair for slot-to-slot progression within the active chain:
    var fxPingFBO = FBO(width, height)
    var fxPongFBO = FBO(width, height)
    // Holds the active chain's dry/wet-blended output:
    var fxOutFBO = FBO(width, height)

    var activeOutputTexture: Int = cleanFBO.texture

    /** Read-only view of the deck's FX filter slots. */
    val fxSlots: Array<llm.slop.liquidlsd.rendering.isf.ISFFilter?>
        get() = fxChain.slots

    fun toFxSlotDto(slotIndex: Int): FXSlotDto? = fxChain.toFxSlotDto(slotIndex)

    fun applyFxSlot(slotIndex: Int, dto: FXSlotDto) = fxChain.applyFxSlot(slotIndex, dto)

    fun clearFxSlot(slotIndex: Int) = fxChain.clearFxSlot(slotIndex)

    fun applyFxChain(dto: FXChainDto) = fxChain.applyFxChain(dto)

    fun toFxChainDto(name: String = "", tags: List<String> = emptyList()): FXChainDto =
        fxChain.toFxChainDto(name, tags)

    val fxEffectiveWet: Float
        get() = if (fxChain.enabled) fxChain.dryWet.value else 0.0f

    fun resize(newWidth: Int, newHeight: Int) {
        if (width == newWidth && height == newHeight) return
        width = newWidth
        height = newHeight
        cleanFBO.dispose()
        fxPingFBO.dispose()
        fxPongFBO.dispose()
        fxOutFBO.dispose()

        cleanFBO = FBO(width, height)
        fxPingFBO = FBO(width, height)
        fxPongFBO = FBO(width, height)
        fxOutFBO = FBO(width, height)

        cleanFBO.clear(0f, 0f, 0f, 0f)
        fxPingFBO.clear(0f, 0f, 0f, 0f)
        fxPongFBO.clear(0f, 0f, 0f, 0f)
        fxOutFBO.clear(0f, 0f, 0f, 0f)
        activeOutputTexture = cleanFBO.texture
    }

    // Deck-level 2D framing, applied to 2D sources by the vertex stage (3D sources frame themselves)
    val viewZoom = ModulatableParameter(1.0f, minClamp = 0.1f, maxClamp = 5.0f)
    val viewRotateZ = ModulatableParameter(0.0f, minClamp = -3.14159f, maxClamp = 3.14159f, meterType = llm.slop.liquidlsd.parameters.MeterType.ENDLESS, explicitIsAngle = true)

    init {
        // Clear all FBOs at startup to prevent reading uninitialized GPU memory
        cleanFBO.clear(0f, 0f, 0f, 0f)
        fxPingFBO.clear(0f, 0f, 0f, 0f)
        fxPongFBO.clear(0f, 0f, 0f, 0f)
        fxOutFBO.clear(0f, 0f, 0f, 0f)
    }

    /** Empties the deck's source side. The FX chain is left alone: FX is separate from presets (Eject, empty presets). */
    fun reset() {
        isEmpty = true
        // An empty deck doesn't render: free the source's buffers (a later pick or undo re-creates them).
        source.dispose()
        viewZoom.reset()
        viewRotateZ.reset()

        // Clear active FBOs
        cleanFBO.clear(0f, 0f, 0f, 0f)
        fxPingFBO.clear(0f, 0f, 0f, 0f)
        fxPongFBO.clear(0f, 0f, 0f, 0f)
        fxOutFBO.clear(0f, 0f, 0f, 0f)
        activeOutputTexture = cleanFBO.texture
        morphController.initFromCurrentState()
    }

    /**
     * Retrieves all randomizable parameters: the source's, its Gain, the FX chain's Dry/Wet and enabled slots, and the
     * deck framing. So the deck die and Rand roll the deck's effects too (only the inputs that ship a random range).
     */
    fun getAllRandomizableParameters(): List<ModulatableParameter> {
        val allParams = mutableListOf<ModulatableParameter>()
        allParams.addAll(this.source.parameters.values)
        allParams.add(this.source.globalAlpha)
        
        allParams.add(fxChain.dryWet)
        allParams.addAll(fxChain.slotRandomizableParameters())

        allParams.add(this.viewZoom)
        allParams.add(this.viewRotateZ)
        return allParams
    }

    val morphController = DeckMorphController(sourceProvider = { source }, paramsProvider = ::getAllRandomizableParameters)

    /**
     * Retrieves the final output texture of the Deck (the active stage texture).
     */
    fun getOutputTexture(): Int = activeOutputTexture

    /**
     * Updates the underlying visual source, the FX chain and the framing parameters.
     */
    fun update() {
        source.update()
        fxChain.update()
        viewZoom.evaluate()
        viewRotateZ.evaluate()
    }

    /**
     * Re-randomizes modulators and base values for all randomizable parameters in this Deck.
     */
    fun randomizeModulators() {
        morphController.forceRandomize()
    }

    /**
     * Disposes all FBOs associated with this Deck.
     */
    fun dispose() {
        cleanFBO.dispose()
        fxPingFBO.dispose()
        fxPongFBO.dispose()
        fxOutFBO.dispose()
        fxChain.dispose()
        source.dispose()
    }

    override fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        
        // The source's Gain sits at the deck level so a mapping to it keeps working whatever the deck plays
        list.add("$prefix/Gain" to source.globalAlpha)
        list.addAll(source.getParameterPaths(prefix))

        // This deck's dedicated FX chain parameters
        list.addAll(fxChain.getParameterPaths("$prefix/FX"))

        // Deck framing
        list.add("$prefix/View/Zoom" to viewZoom)
        list.add("$prefix/View/RotateZ" to viewRotateZ)
        
        return list
    }
}
