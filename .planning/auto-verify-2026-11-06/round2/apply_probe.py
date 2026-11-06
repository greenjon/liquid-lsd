#!/usr/bin/env python3
"""Run in a THROWAWAY worktree: adds test-only hooks (never commit these). Window size env, audit/state/mod tour actions, toast log, label-keyed widgets."""
import glob,re
p='src/main/kotlin/llm/slop/liquidlsd/Main.kt'; s=open(p).read()
s=s.replace('val initialWinW = if (tour != null) 1920 else','val initialWinW = if (tour != null) (System.getenv("LSD_TOUR_W")?.toInt() ?: 1920) else')
s=s.replace('val initialWinH = if (tour != null) 1080 else','val initialWinH = if (tour != null) (System.getenv("LSD_TOUR_H")?.toInt() ?: 1080) else')
open(p,'w').write(s)
p='src/main/kotlin/llm/slop/liquidlsd/Main.kt'; s=open(p).read(); s=s.replace('        UITheme.manualLoadDirtyBehavior = UITheme.ManualLoadDirtyBehavior.DISCARD','        if (System.getenv("LSD_TOUR_W") == null) UITheme.manualLoadDirtyBehavior = UITheme.ManualLoadDirtyBehavior.DISCARD'); open(p,'w').write(s)
p='src/main/kotlin/llm/slop/liquidlsd/ui/ToastOverlay.kt'; s=open(p).read()
a='        current.set(Toast(text,'; assert a in s
s=s.replace(a,'        println("TOUR_TOAST $text")\n'+a); open(p,'w').write(s)
p='src/main/kotlin/llm/slop/liquidlsd/tour/TourRunner.kt'; s=open(p).read()
s=s.replace('''                    name == "backspace" -> imgui.flag.ImGuiKey.Backspace
''','''                    name == "backspace" -> imgui.flag.ImGuiKey.Backspace
                    name == "delete" -> imgui.flag.ImGuiKey.Delete
                    name == "down" -> imgui.flag.ImGuiKey.DownArrow
                    name == "up" -> imgui.flag.ImGuiKey.UpArrow
''')
a='''            "fx_filter" -> fxChain(step)?.let { c ->'''; assert s.count(a)==1
s=s.replace(a,'''            "mod" -> {
                val down = (step.value ?: 1f) >= 0.5f
                val (m, k) = when (step.target) { "ctrl" -> imgui.flag.ImGuiKey.ImGuiMod_Ctrl to imgui.flag.ImGuiKey.LeftCtrl; "shift" -> imgui.flag.ImGuiKey.ImGuiMod_Shift to imgui.flag.ImGuiKey.LeftShift; else -> imgui.flag.ImGuiKey.ImGuiMod_Alt to imgui.flag.ImGuiKey.LeftAlt }
                inputQueue.add { it.addKeyEvent(m, down); it.addKeyEvent(k, down) }
            }
            "audit_state" -> mixer?.let { mx ->
                val sb = StringBuilder("TOUR_STATE ${step.target} t=${step.t}")
                for (ds in llm.slop.liquidlsd.presets.DeckSlot.entries) {
                    val d = ds.deck(mx); val ch = d.fxChain
                    sb.append(" | ${ds.name}_chain: name=${ch.name} file=${ch.sourceFile?.name} dirty=${ch.isDirty()} on=${ch.enabled} wet=${ch.dryWet.baseValue}")
                    sb.append(" | ${ds.name}: src=${if (d.isEmpty) "EMPTY" else d.source.displayName} file=${llm.slop.liquidlsd.presets.PresetManager.activeFile(ds)?.name} dirty=${llm.slop.liquidlsd.presets.DeckOps.isDirty(d, mx)} fx=${ch.slots.map { it?.displayName ?: "-" }}")
                }
                sb.append(" | M_chain: name=${mx.masterFxChain.name} file=${mx.masterFxChain.sourceFile?.name} dirty=${mx.masterFxChain.isDirty()}")
                sb.append(" | M: fx=${mx.masterFxChain.slots.map { it?.displayName ?: "-" }} trans=${mx.transitionFilter?.displayName} xf=${mx.crossfade.baseValue}")
                for (q in llm.slop.liquidlsd.ui.browser.QueueDrop.Queue.entries) {
                    val snap = llm.slop.liquidlsd.ui.browser.QueueOps.snapshot(q)
                    sb.append(" | Q${q.name}: ${snap.files.map { it.nameWithoutExtension }} active=${snap.active} cur=${snap.cursor}")
                }
                sb.append(" | page=${UITheme.performancePageId} lib=${UITheme.libraryMode}")
                println(sb)
            }
            "audit_list" -> auditParams(step).forEach { (n, q) -> println("TOUR_AUDIT list\\t${step.deck}/${step.slot ?: "src"}/${step.target}\\t$n\\tmin=${q.minClamp}\\tmax=${q.maxClamp}\\tdef=${q.defaultValue}\\tbase=${q.baseValue}\\tcur=${q.value}\\tsteps=${q.steps}\\tangle=${q.isAngle}\\tmeta=${q.metaDrivenBy}\\tdriver=${q.baseDriver != null}\\trand=${q.randomizeBase}") }
            "audit_set" -> mixer?.let { mx ->
                val name = step.param!!
                val q = auditParams(step).toMap()[name]
                if (q == null) { println("TOUR_AUDIT missing $name"); return }
                if (step.slot != null) fxChain(step)?.slots?.get(step.slot)?.metaBindings?.removeAll { it.targetParamName == name }
                val path = llm.slop.liquidlsd.parameters.ParameterResolver.getAllParameterPaths(mx).firstOrNull { it.second === q }?.first
                if (path == null) { println("TOUR_AUDIT nopath $name"); return }
                for (id in llm.slop.liquidlsd.macro.MacroEngine.CANONICAL_BANK_IDS) llm.slop.liquidlsd.macro.MacroEngine.getBank(id)?.knobs?.forEach { k -> k.bindings.removeAll { it.parameterId == path && it.targetType == llm.slop.liquidlsd.macro.MacroTargetType.PARAM_BASE_VALUE } }
                val knob = llm.slop.liquidlsd.macro.MacroEngine.getBank(llm.slop.liquidlsd.macro.MacroEngine.DECK_A)!!.knobs[step.unit?.toInt() ?: 3]
                knob.bindings.clear()
                knob.bindings.add(llm.slop.liquidlsd.macro.MacroBinding(parameterId = path, targetType = llm.slop.liquidlsd.macro.MacroTargetType.PARAM_BASE_VALUE, minVal = q.minClamp, maxVal = q.maxClamp))
                knob.value = step.value ?: 0f
                llm.slop.liquidlsd.macro.MacroEngine.invalidate()
                auditWatch = Triple(name, q, step.value ?: 0f)
            }
            "audit_read" -> auditWatch?.let { (n, q, v) ->
                println("TOUR_AUDIT read\\tt=${step.t}\\t$n\\tknob=$v\\twant=${q.minClamp + (q.maxClamp - q.minClamp) * v}\\tbase=${q.baseValue}\\tmeta=${q.metaDrivenBy}\\tdriver=${q.baseDriver != null}")
            }
'''+a)
a='''    /** Sets a parameter now, or with durationSec eases'''; assert s.count(a)==1
s=s.replace(a,'''    private var auditWatch: Triple<String, llm.slop.liquidlsd.parameters.ModulatableParameter, Float>? = null
    private fun auditParams(step: TourStep): List<Pair<String, llm.slop.liquidlsd.parameters.ModulatableParameter>> {
        val mx = mixer ?: return emptyList()
        return when {
            step.deck.equals("TRANS", true) -> mx.transitionFilter?.parameters?.toList() ?: emptyList()
            step.slot != null -> fxChain(step)?.slots?.get(step.slot)?.parameters?.toList() ?: emptyList()
            else -> llm.slop.liquidlsd.presets.DeckSlot.entries.first { it.name.equals(step.deck, true) }.deck(mx).source.parameters.toList()
        }
    }

'''+a)
open(p,'w').write(s)
open('src/main/kotlin/llm/slop/liquidlsd/tour/Ui.kt','w').write('''package llm.slop.liquidlsd.tour
import imgui.ImGui
object Ui {
    private fun k(p: String, l: String) { if (WidgetRegistry.enabled) WidgetRegistry.noteLastItem(p + l.substringBefore("##").filter { it.code < 128 }.trim()) }
    fun menuItem(l: String): Boolean = ImGui.menuItem(l).also { k("m:", l) }
    fun menuItem(l: String, s: Boolean): Boolean = ImGui.menuItem(l, s).also { k("m:", l) }
    fun menuItem(l: String, a: Boolean, b: Boolean): Boolean = ImGui.menuItem(l, a, b).also { k("m:", l) }
    fun menuItem(l: String, s: String?): Boolean = ImGui.menuItem(l, s ?: "").also { k("m:", l) }
    fun menuItem(l: String, s: String?, a: Boolean): Boolean = ImGui.menuItem(l, s ?: "", a).also { k("m:", l) }
    fun menuItem(l: String, s: String?, a: Boolean, b: Boolean): Boolean = ImGui.menuItem(l, s ?: "", a, b).also { k("m:", l) }
    fun beginMenu(l: String): Boolean = ImGui.beginMenu(l).also { k("m:", l) }
    fun beginMenu(l: String, b: Boolean): Boolean = ImGui.beginMenu(l, b).also { k("m:", l) }
    fun selectable(l: String): Boolean = ImGui.selectable(l).also { k("s:", l) }
    fun selectable(l: String, a: Boolean): Boolean = ImGui.selectable(l, a).also { k("s:", l) }
    fun selectable(l: String, a: Boolean, f: Int): Boolean = ImGui.selectable(l, a, f).also { k("s:", l) }
    fun selectable(l: String, a: Boolean, f: Int, w: Float, h: Float): Boolean = ImGui.selectable(l, a, f, w, h).also { k("s:", l) }
    fun button(l: String): Boolean = ImGui.button(l).also { k("b:", l) }
    fun button(l: String, w: Float, h: Float): Boolean = ImGui.button(l, w, h).also { k("b:", l) }
    fun smallButton(l: String): Boolean = ImGui.smallButton(l).also { k("b:", l) }
}
''')
for f in glob.glob('src/main/kotlin/llm/slop/liquidlsd/ui/**/*.kt',recursive=True):
    t=open(f).read(); n=re.sub(r'\bImGui\.(menuItem|beginMenu|selectable|button|smallButton)\(',r'llm.slop.liquidlsd.tour.Ui.\1(',t)
    if n!=t: open(f,'w').write(n)
