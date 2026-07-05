package llm.slop.spirals.parameters

import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.Mandala
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.rendering.DynamicVisualSource

object ParameterResolver {
    fun getAllParameterPaths(mixer: Mixer): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        
        // Mixer
        list.add("Deck A/FB/Source" to mixer.deckA.sourceSelect)
        list.add("Deck B/FB/Source" to mixer.deckB.sourceSelect)
        list.add("Mixer/crossfade" to mixer.crossfade)
        list.add("Mixer/masterAlpha" to mixer.masterAlpha)
        list.add("Mixer/bloom" to mixer.bloom)
        list.add("Mixer/xfadeSpeed" to mixer.xfadeSpeed)
        list.add("Mixer/queuePrev" to mixer.queuePrev)
        list.add("Mixer/queueNext" to mixer.queueNext)
        
        // Deck A and B
        for (deckLabel in listOf("Deck A", "Deck B")) {
            val deck = if (deckLabel == "Deck A") mixer.deckA else mixer.deckB
            val mandala = deck.source as? Mandala
            
            if (mandala != null) {
                // Geometry
                list.add("$deckLabel/Geometry/Lobes" to mandala.parameters["Lobes"]!!)
                list.add("$deckLabel/Geometry/Recipe" to mandala.parameters["Recipe Select"]!!)
                list.add("$deckLabel/Geometry/L1" to mandala.parameters["L1"]!!)
                list.add("$deckLabel/Geometry/L2" to mandala.parameters["L2"]!!)
                list.add("$deckLabel/Geometry/L3" to mandala.parameters["L3"]!!)
                list.add("$deckLabel/Geometry/L4" to mandala.parameters["L4"]!!)
                list.add("$deckLabel/Geometry/FreqOffset" to mandala.parameters["Freq Offset"]!!)
                list.add("$deckLabel/Geometry/HarmonicLock" to mandala.parameters["Harmonic Lock"]!!)
                list.add("$deckLabel/Geometry/3DMode" to mandala.parameters["3D Mode"]!!)
                list.add("$deckLabel/Geometry/SphereWrapX" to mandala.parameters["Sphere Wrap X"]!!)
                list.add("$deckLabel/Geometry/SphereWrapY" to mandala.parameters["Sphere Wrap Y"]!!)
                list.add("$deckLabel/Geometry/MirrorGroup" to mandala.parameters["Mirror Group"]!!)
                list.add("$deckLabel/Geometry/PermuteXY" to mandala.parameters["Permute XY"]!!)
                list.add("$deckLabel/Geometry/PermuteYZ" to mandala.parameters["Permute YZ"]!!)
                list.add("$deckLabel/Geometry/PermuteZX" to mandala.parameters["Permute ZX"]!!)

                // View
                list.add("$deckLabel/View/Zoom" to mandala.parameters["Zoom"]!!)
                list.add("$deckLabel/View/RotateZ" to mandala.parameters["Rotate Z"]!!)
                list.add("$deckLabel/View/RotateX" to mandala.parameters["Rotate X"]!!)
                list.add("$deckLabel/View/RotateY" to mandala.parameters["Rotate Y"]!!)
                list.add("$deckLabel/View/Persp" to mandala.parameters["3D Persp"]!!)
                
                // Color
                list.add("$deckLabel/Color/Thickness" to mandala.parameters["Thickness"]!!)
                list.add("$deckLabel/Color/HueOffset" to mandala.parameters["Hue Offset"]!!)
                list.add("$deckLabel/Color/HueSweep" to mandala.parameters["Hue Sweep"]!!)
                list.add("$deckLabel/Color/Depth" to mandala.parameters["Depth"]!!)
                list.add("$deckLabel/Color/Gain" to mandala.globalAlpha)
                
                // Background
                list.add("$deckLabel/Background/Style" to mandala.parameters["Bg Style"]!!)
                list.add("$deckLabel/Background/Feedback" to mandala.parameters["Bg Feedback"]!!)
                list.add("$deckLabel/Background/Hue" to mandala.parameters["Bg Hue"]!!)
                list.add("$deckLabel/Background/Sat" to mandala.parameters["Bg Sat"]!!)
                list.add("$deckLabel/Background/Val" to mandala.parameters["Bg Val"]!!)
                list.add("$deckLabel/Background/Sweep" to mandala.parameters["Bg Sweep"]!!)
                list.add("$deckLabel/Background/Speed" to mandala.parameters["Bg Speed"]!!)
                list.add("$deckLabel/Background/Zoom" to mandala.parameters["Bg Zoom"]!!)
            }

            val activeSource = deck.source
            if (activeSource is DynamicVisualSource) {
                activeSource.parameters.forEach { (name, param) ->
                    list.add("$deckLabel/${activeSource.displayName}/$name" to param)
                }
                list.add("$deckLabel/${activeSource.displayName}/Gain" to activeSource.globalAlpha)
            }
            
            // Feedback
            list.add("$deckLabel/FB/Decay" to deck.fbDecay)
            list.add("$deckLabel/FB/Gain" to deck.fbGain)
            list.add("$deckLabel/FB/Zoom" to deck.fbZoom)
            list.add("$deckLabel/FB/Rotate" to deck.fbRotate)
            list.add("$deckLabel/FB/HueShift" to deck.fbHueShift)
            list.add("$deckLabel/FB/Blur" to deck.fbBlur)
            list.add("$deckLabel/FB/Chroma" to deck.fbChroma)
            list.add("$deckLabel/FB/Mode" to deck.fbMode)
        }
        
        return list
    }

    fun findParameterByPath(mixer: Mixer, path: String): ModulatableParameter? {
        // Simple O(N) search against the list. We could optimize this by caching if performance is an issue,
        // but it is usually only called when restoring UI state or handling midi mappings occasionally.
        return getAllParameterPaths(mixer).find { it.first == path }?.second
    }
}
