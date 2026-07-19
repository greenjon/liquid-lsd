package llm.slop.liquidlsd

import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.midi.MidiMappingManager
import llm.slop.liquidlsd.patches.PatchManager
import llm.slop.liquidlsd.patches.PlayQueueManager
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import llm.slop.liquidlsd.ui.UITheme

class SessionContext {
    val cvRegistry = CVRegistry
    val audioEngine = AudioEngine
    val patchManager = PatchManager
    val playQueueManager = PlayQueueManager
    val midiMappingManager = MidiMappingManager
    val visualSourceRegistry = VisualSourceRegistry
    val uiTheme = UITheme
}
