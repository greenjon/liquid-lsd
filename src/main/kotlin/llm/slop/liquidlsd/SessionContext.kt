package llm.slop.liquidlsd

import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.midi.MidiMappingManager
import llm.slop.liquidlsd.broadcast.BroadcastEngine
import llm.slop.liquidlsd.presets.PresetManager
import llm.slop.liquidlsd.presets.PlayQueueManager
import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import llm.slop.liquidlsd.ui.UITheme

class SessionContext {
    val cvRegistry = CVRegistry
    val audioEngine = AudioEngine
    val presetManager = PresetManager
    val playQueueManager = PlayQueueManager
    val bgQueueManager = BgQueueManager
    val midiMappingManager = MidiMappingManager
    val visualSourceRegistry = VisualSourceRegistry
    val uiTheme = UITheme
    val broadcastEngine = BroadcastEngine
}

