#!/usr/bin/env python3
"""Run after round2/apply_probe.py in the THROWAWAY worktree. Adds a fake Twister: LSD_FAKE_TWISTER makes the controller manager see a device and log what it is sent (TOUR_MIDI_OUT); a `midi` tour action feeds its input through MidiEngine's real queue; audit_state also reports the UI state and the 16 knob lights. Never commit these."""
p='src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt'; s=open(p).read()
a='    val controllers = ControllerManager(commands)\n'; assert a in s
s=s.replace(a,'''    val controllers = if (System.getenv("LSD_FAKE_TWISTER") != null) ControllerManager(commands, connectedDevices = { listOf("Midi Fighter Twister") }, openSink = { _, _ -> object : llm.slop.liquidlsd.control.MidiSink {
        override fun sendCc(channel: Int, cc: Int, value: Int) { println("TOUR_MIDI_OUT cc $channel $cc $value") }
        override fun sendSysex(bytes: ByteArray) { println("TOUR_MIDI_OUT sysex " + bytes.joinToString(" ") { "%02X".format(it) }) }
    } }) else ControllerManager(commands)
''')
open(p,'w').write(s)
p='src/main/kotlin/llm/slop/liquidlsd/control/ControllerManager.kt'; s=open(p).read()
a='    private val lightBuffer = arrayOfNulls<KnobLight>(KnobCommands.KNOB_COUNT)\n'; assert a in s
s=s.replace(a,'    val lightBuffer = arrayOfNulls<KnobLight>(KnobCommands.KNOB_COUNT)\n    val runtimesForProbe get() = runtimes\n')
open(p,'w').write(s)
p='src/main/kotlin/llm/slop/liquidlsd/Main.kt'; s=open(p).read()
a='llm.slop.liquidlsd.control.ControllerProfileStore.default.preferredIdFor = { llm.slop.liquidlsd.ui.UITheme.controllerProfileChoices[it] }'; assert a in s
s=s.replace(a,'llm.slop.liquidlsd.control.ControllerProfileStore.default.preferredIdFor = { System.getenv("LSD_FAKE_TWISTER_PROFILE") ?: llm.slop.liquidlsd.ui.UITheme.controllerProfileChoices[it] }')
open(p,'w').write(s)
p='src/main/kotlin/llm/slop/liquidlsd/tour/TourRunner.kt'; s=open(p).read()
a='''            "mod" -> {
'''; assert s.count(a)==1
s=s.replace(a,'''            "midi" -> {
                val (ch, cc, v) = step.target!!.split(":").map { it.toInt() }
                llm.slop.liquidlsd.midi.MidiEngine.enqueueEvent(llm.slop.liquidlsd.midi.MidiEvent(ch, llm.slop.liquidlsd.midi.MidiMessageType.CC, cc, v, v / 127f, deviceId = "Midi Fighter Twister"))
            }
'''+a)
a='''                sb.append(" | page=${UITheme.performancePageId} lib=${UITheme.libraryMode}")
'''; assert s.count(a)==1
s=s.replace(a,a+'''                parametersState?.let { ps -> sb.append(" | ui: pair=${ps.focusedPair} chain=${ps.chainView} live=${ps.libraryLivePair} dock=${ps.dockSelection?.let { d -> d.moduleId + "/" + d.target }}") }
                val mgr = llm.slop.liquidlsd.midi.MidiMappingManager.controllers
                sb.append(" | bank: ${mgr.runtimeFor("Midi Fighter Twister")?.activeBank}")
                sb.append(" | knobs: " + (mgr.lightBuffer.joinToString(";") { l -> if (l == null) "-" else "%.3f,%.2f,%.2f,%.2f,%d,%s,%.2f,%d".format(l.value, l.r, l.g, l.b, if (l.lit) 1 else 0, l.meterType.name[0], l.ringBrightness, if (l.marker) 1 else 0) }))
''')
open(p,'w').write(s)
# MIDI stays on for the fake device, but the engine never opens a real port.
p='src/main/kotlin/llm/slop/liquidlsd/Main.kt'; s=open(p).read()
s=s.replace('        UITheme.midiEnabled = false\n','        UITheme.midiEnabled = System.getenv("LSD_FAKE_TWISTER") != null\n')
s=s.replace('enabled = { llm.slop.liquidlsd.ui.UITheme.midiEnabled }','enabled = { System.getenv("LSD_FAKE_TWISTER") == null && llm.slop.liquidlsd.ui.UITheme.midiEnabled }')
s=s.replace('\n    if (llm.slop.liquidlsd.ui.UITheme.midiEnabled) {','\n    if (System.getenv("LSD_FAKE_TWISTER") == null && llm.slop.liquidlsd.ui.UITheme.midiEnabled) {')
open(p,'w').write(s)
# Unplug/replug, Super Knob response, tempo readout.
p='src/main/kotlin/llm/slop/liquidlsd/midi/MidiMappingManager.kt'; s=open(p).read()
s=s.replace('connectedDevices = { listOf("Midi Fighter Twister") }','connectedDevices = { if (llm.slop.liquidlsd.tour.FakePlug.on) listOf("Midi Fighter Twister") else emptyList() }')
open(p,'w').write(s)
open('src/main/kotlin/llm/slop/liquidlsd/tour/FakePlug.kt','w').write('package llm.slop.liquidlsd.tour\nobject FakePlug { @Volatile var on = true }\n')
p='src/main/kotlin/llm/slop/liquidlsd/tour/TourRunner.kt'; s=open(p).read()
a='''            "midi" -> {
'''; assert s.count(a)==1
s=s.replace(a,'''            "fake_plug" -> FakePlug.on = (step.value ?: 1f) >= .5f
            "super_mode" -> UITheme.superKnobMode = llm.slop.liquidlsd.rendering.SuperKnobMode.valueOf(step.target!!)
'''+a)
a='''                sb.append(" | bank: '''; assert s.count(a)==1
s=s.replace(a,'''                sb.append(" | bpm: ${llm.slop.liquidlsd.audio.AudioEngine.getEstimatedBpm()}")
'''+a)
open(p,'w').write(s)
# View state, prompt state, chain links and per-frame CV signals; set Audio Response.
p='src/main/kotlin/llm/slop/liquidlsd/tour/TourRunner.kt'; s=open(p).read()
a='''                sb.append(" | bank: '''; assert s.count(a)==1
s=s.replace(a,'''                for (ds in llm.slop.liquidlsd.presets.DeckSlot.entries) { val c = ds.deck(mx).fxChain; sb.append(" | ${ds.name}_fx: links=${c.slotSuperKnobLink.toList()} metas=${c.slots.map { it?.metaKnob?.baseValue }} super=${c.superKnob.baseValue} wait=${(0..2).map { c.isAwaitingPickup(it) }}") }
'''+a)
a='''        maxBass = maxOf(maxBass, llm.slop.liquidlsd.cv.CVRegistry.get("audio_bass"))
'''; assert s.count(a)==1
s=s.replace(a,a+'''        if (System.getenv("LSD_TOUR_CV") != null) println("TOUR_CV %.3f %.3f %.3f %.3f %.3f %.3f %.3f %.1f".format(java.util.Locale.ROOT, frame / fps.toDouble(), llm.slop.liquidlsd.cv.CVRegistry.get("audio_bass"), llm.slop.liquidlsd.cv.CVRegistry.get("audio_amp"), llm.slop.liquidlsd.cv.CVRegistry.get("audio_flux_bass"), llm.slop.liquidlsd.cv.CVRegistry.get("audio_energy"), llm.slop.liquidlsd.cv.CVRegistry.get("audio_drop"), llm.slop.liquidlsd.cv.CVRegistry.get("audio_energy_bass"), llm.slop.liquidlsd.audio.AudioEngine.getEstimatedBpm()))
''')
a='''            "midi" -> {
'''; assert s.count(a)==1
s=s.replace(a,'''            "audio_response" -> llm.slop.liquidlsd.audio.AudioEngine.response = step.value ?: 1f
            "off_air" -> UITheme.offAirHalfRate = (step.value ?: 1f) >= .5f
'''+a)
open(p,'w').write(s)
# Memory readout for the long run.
p='src/main/kotlin/llm/slop/liquidlsd/tour/TourRunner.kt'; s=open(p).read()
a='''                sb.append(" | bank: '''; assert s.count(a)==1
s=s.replace(a,'''                sb.append(" | mem: fbo=${llm.slop.liquidlsd.ui.PerformanceStats.fboCount} mb=${llm.slop.liquidlsd.ui.PerformanceStats.fboMemoryMB} heap=${(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1000000}")
'''+a)
open(p,'w').write(s)
