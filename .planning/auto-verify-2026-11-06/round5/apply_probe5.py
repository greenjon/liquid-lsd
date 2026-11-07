#!/usr/bin/env python3
"""Run after round4/apply_probe4.py in the throwaway worktree. Macro bindings, MIDI mappings and a parameter dump for scripted checks. Never commit."""
p='src/main/kotlin/llm/slop/liquidlsd/tour/TourRunner.kt'; s=open(p).read()
a='''            "audit_keys" -> '''; assert s.count(a)==1
hooks='''            // macro_bind: bank in `deck` (deckA, master...), knob in `unit`, path in `param`; target "curve,inverted,zone" or "mod,<index>,<property>"
            "macro_bind" -> mixer?.let { mx ->
                val bank = llm.slop.liquidlsd.macro.MacroEngine.getBank(step.deck!!) ?: run { println("TOUR_WARN no bank ${step.deck}"); return }
                val knob = bank.knobs[step.unit!!.toInt()]
                val t = (step.target ?: "").split(",")
                val b = if (t.firstOrNull() == "mod") {
                    val q = llm.slop.liquidlsd.parameters.ParameterResolver.findParameterByPath(mx, step.param!!)!!
                    val m = q.modulators[t[1].toInt()]
                    llm.slop.liquidlsd.macro.MacroBinding(parameterId = step.param!!, targetType = llm.slop.liquidlsd.macro.MacroTargetType.MODULATOR_PROPERTY, modulatorId = m.id, modulatorIndex = t[1].toInt(), propertyName = t[2])
                } else llm.slop.liquidlsd.macro.MacroBinding(parameterId = step.param!!, targetType = llm.slop.liquidlsd.macro.MacroTargetType.PARAM_BASE_VALUE).also { b ->
                    if (t.size > 0 && t[0].isNotBlank()) b.curve = llm.slop.liquidlsd.macro.MacroCurveType.valueOf(t[0])
                    if (t.size > 1) b.inverted = t[1] == "1"
                    if (t.size > 2) b.linkMode = llm.slop.liquidlsd.macro.MacroLinkMode.valueOf(t[2])
                }
                knob.bindings.add(b); llm.slop.liquidlsd.macro.MacroEngine.invalidate()
            }
            "macro_unbind" -> { llm.slop.liquidlsd.macro.MacroEngine.getBank(step.deck!!)?.knobs?.get(step.unit!!.toInt())?.bindings?.clear(); llm.slop.liquidlsd.macro.MacroEngine.invalidate() }
            "macro_set" -> { llm.slop.liquidlsd.macro.MacroEngine.getBank(step.deck!!)?.knobs?.get(step.unit!!.toInt())?.value = step.value!! }
            "midi_map" -> llm.slop.liquidlsd.midi.MidiMappingManager.addMapping(step.param!!, step.unit!!.toInt(), 0)
            "audit_param" -> mixer?.let { mx ->
                val q = llm.slop.liquidlsd.parameters.ParameterResolver.findParameterByPath(mx, step.param!!)
                val bank = step.deck?.let { llm.slop.liquidlsd.macro.MacroEngine.getBank(it) }
                println("TOUR_PARAM ${step.target} base=${q?.baseValue} min=${q?.baseMin} max=${q?.baseMax} rnd=${q?.randomizeBase} meta=${q?.metaDrivenBy} driver=${q?.baseDriver != null} " +
                    "suspend=${llm.slop.liquidlsd.macro.MacroEngine.mappingSuspendReason(step.param!!)} knobs=${bank?.knobs?.joinToString(",") { "%.3f".format(it.value) }} " +
                    "binds=${bank?.knobs?.joinToString(";") { k -> k.bindings.joinToString("+") { "${it.parameterId}:${it.modulatorId}:${it.propertyName}" } }} " +
                    "mods=${q?.modulators?.joinToString(";") { "${it.id}:${it.sourceId}:min=${it.getLfoMin()}:max=${it.getLfoMax()}:depth=${it.depth}" }}")
            }
'''
s=s.replace(a,hooks+a)
open(p,'w').write(s)
