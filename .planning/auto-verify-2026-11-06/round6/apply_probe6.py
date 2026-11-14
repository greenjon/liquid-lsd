#!/usr/bin/env python3
"""Run after round5/apply_probe5.py in the throwaway worktree. Never commit."""
p='src/main/kotlin/llm/slop/liquidlsd/Main.kt'; s=open(p).read()
a='        UITheme.startupBehavior = UITheme.StartupBehavior.EMPTY\n'; assert s.count(a)==1
s=s.replace(a,'        UITheme.startupBehavior = System.getenv("LSD_TOUR_STARTUP")?.let { UITheme.StartupBehavior.valueOf(it) } ?: UITheme.StartupBehavior.EMPTY\n')
open(p,'w').write(s)
p='src/main/kotlin/llm/slop/liquidlsd/tour/TourRunner.kt'; s=open(p).read()
a='''            "audit_keys" -> '''; assert s.count(a)==1
s=s.replace(a,'''            "audit_chain" -> mixer?.let { mx ->
                val c = if (step.deck == "MASTER") mx.masterFxChain else llm.slop.liquidlsd.presets.DeckSlot.entries.first { it.name == step.deck }.deck(mx).fxChain
                val j = llm.slop.liquidlsd.presets.PresetManager.json
                println("TOUR_CHAINB ${step.target} " + j.encodeToString(llm.slop.liquidlsd.models.FXChainDto.serializer(), c.baselineDto ?: c.toFxChainDto()).replace("\\n", ""))
                println("TOUR_CHAINC ${step.target} " + j.encodeToString(llm.slop.liquidlsd.models.FXChainDto.serializer(), c.toFxChainDto()).replace("\\n", ""))
            }
'''+a)
open(p,'w').write(s)
p='src/main/kotlin/llm/slop/liquidlsd/tour/TourRunner.kt'; s=open(p).read()
a='''            "audit_keys" -> '''; assert s.count(a)==1
s=s.replace(a,'''            "midi_map_range" -> mixer?.let { mx -> val q = llm.slop.liquidlsd.parameters.ParameterResolver.findParameterByPath(mx, step.param!!); if (q == null) println("TOUR_WARN no param ${step.param}") else llm.slop.liquidlsd.midi.MidiMappingManager.addMapping(step.param!!, step.unit!!.toInt(), 0, minVal = q.minClamp, maxVal = q.maxClamp) }
'''+a)
open(p,'w').write(s)
