#!/usr/bin/env python3
"""Run after round3/apply_probe3.py in the throwaway worktree. For the random-input run: Exit only logs, so a stray click can't end the run. Never commit."""
p='src/main/kotlin/llm/slop/liquidlsd/ui/UIManager.kt'; s=open(p).read()
a='onTriggerExit = { org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose('; assert s.count(a)==1
s=s.replace(a,'onTriggerExit = { if (System.getenv("LSD_MONKEY") != null) println("TOUR_EXIT_REQUEST") else org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(')
open(p,'w').write(s)
# Which widgets are on screen; a dump of Metaknob targets.
p='src/main/kotlin/llm/slop/liquidlsd/tour/TourRunner.kt'; s=open(p).read()
a='''            "midi" -> {
'''; assert s.count(a)==1
s=s.replace(a,'''            "audit_keys" -> println("TOUR_KEYS ${step.target} " + WidgetRegistry.keys().filter { WidgetRegistry.isCurrent(it) }.sorted().joinToString("|"))
            "audit_dump" -> mixer?.let { mx ->
                val out = StringBuilder("TOUR_DUMP ${step.target} ")
                for (ds in llm.slop.liquidlsd.presets.DeckSlot.entries) { val c = ds.deck(mx).fxChain; c.slots.forEachIndexed { i, f -> if (f != null) out.append("|${ds.name}.fx$i=${f.displayName} meta=${f.metaKnob.baseValue} targets=${f.metaBindings.map { b -> b.targetParamName + ":" + b.minVal + ":" + b.maxVal + ":" + b.invert + ":" + b.enabled }}") } }
                out.append("|macro=${llm.slop.liquidlsd.macro.MacroLearnState.isLearning()} osc=${llm.slop.liquidlsd.osc.OscLearnState.isLearning()}")
                println(out)
            }
'''+a)
open(p,'w').write(s)
# Modulators of a parameter and learn states.
p='src/main/kotlin/llm/slop/liquidlsd/tour/TourRunner.kt'; s=open(p).read()
a='''            "audit_keys" -> '''; assert s.count(a)==1
s=s.replace(a,'''            "audit_mods" -> mixer?.let { mx -> val q = llm.slop.liquidlsd.parameters.ParameterResolver.findParameterByPath(mx, step.param!!); println("TOUR_MODS ${step.target} base=${q?.baseValue} " + q?.modulators?.joinToString(";") { it.toString() }) }
'''+a)
open(p,'w').write(s)
# Rectangles of on-screen widgets, for the contrast list.
p='src/main/kotlin/llm/slop/liquidlsd/tour/TourRunner.kt'; s=open(p).read()
a='''            "audit_keys" -> '''; assert s.count(a)==1
s=s.replace(a,'''            "audit_rects" -> println("TOUR_RECTS ${step.target} t=${step.t} " + WidgetRegistry.keys().filter { WidgetRegistry.isCurrent(it) }.joinToString("\\t") { k -> val r = WidgetRegistry.find(k)!!; "$k=${r.minX.toInt()},${r.minY.toInt()},${r.maxX.toInt()},${r.maxY.toInt()}" })
'''+a)
open(p,'w').write(s)
