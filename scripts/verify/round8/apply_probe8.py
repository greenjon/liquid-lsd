#!/usr/bin/env python3
"""Run after apply_probe6.py in the throwaway worktree. Never commit. Adds left/right keys and TOUR_TIP lines."""
p='src/main/kotlin/llm/slop/liquidlsd/tour/TourRunner.kt'; s=open(p).read()
a='''                    name == "up" -> imgui.flag.ImGuiKey.UpArrow\n'''; assert s.count(a)==1
if 'LeftArrow' not in s:
    s=s.replace(a,a+'''                    name == "left" -> imgui.flag.ImGuiKey.LeftArrow\n                    name == "right" -> imgui.flag.ImGuiKey.RightArrow\n                    name == "home" -> imgui.flag.ImGuiKey.Home\n                    name == "end" -> imgui.flag.ImGuiKey.End\n''')
open(p,'w').write(s)
p='src/main/kotlin/llm/slop/liquidlsd/ui/TooltipHelper.kt'; s=open(p).read()
if 'TOUR_TIP' not in s:
    s=s.replace('''    ImGui.beginTooltip()
    ImGui.pushTextWrapPos(ImGui.getCursorPosX() + wrapWidth)
    ImGui.textUnformatted(text)''','''    ImGui.beginTooltip()
    TourTip.log(text)
    ImGui.pushTextWrapPos(ImGui.getCursorPosX() + wrapWidth)
    ImGui.textUnformatted(text)''')
    assert s.count('TourTip.log(text)')==2
    s+='''
object TourTip {
    private val on = System.getenv("LSD_TOUR_TIPS") != null
    private var last = ""
    fun log(text: String) { if (on && text != last) { last = text; println("TOUR_TIP " + text.replace("\\n", " \\\\n ")) } }
}
'''
open(p,'w').write(s)
p='src/main/kotlin/llm/slop/liquidlsd/ui/UITheme.kt'; s=open(p).read()
a='    fun tooltip(text: String) = withFont(FontLevel.TOOLTIP) { ImGui.textUnformatted(text) }'
if a in s:
    s=s.replace(a,'    fun tooltip(text: String) = withFont(FontLevel.TOOLTIP) { TourTip.log(text); ImGui.textUnformatted(text) }')
    s=s.replace('''    fun tooltipColored(r: Float, g: Float, b: Float, a: Float, text: String) =
        withFont(FontLevel.TOOLTIP) {
''','''    fun tooltipColored(r: Float, g: Float, b: Float, a: Float, text: String) =
        withFont(FontLevel.TOOLTIP) {
            TourTip.log(text)
''')
open(p,'w').write(s)
p='src/main/kotlin/llm/slop/liquidlsd/Main.kt'; s=open(p).read()
a='    if (cliArgs.tourMode) AppPreferencesStore.persistenceDisabled = true\n'
if a in s: s=s.replace(a,'    if (cliArgs.tourMode && System.getenv("LSD_TOUR_PERSIST") == null) AppPreferencesStore.persistenceDisabled = true\n')
a='    if (tour == null) SessionSerializer.saveSession(mixer)'
if a in s: s=s.replace(a,'    if (tour == null || System.getenv("LSD_TOUR_PERSIST") != null) SessionSerializer.saveSession(mixer)')
open(p,'w').write(s)
p='src/main/kotlin/llm/slop/liquidlsd/tour/TourRunner.kt'; s=open(p).read()
a='            "scroll" -> {'
if '"chars" ->' not in s:
    s=s.replace(a,'            "chars" -> for (c in step.target.orEmpty()) { inputQueue.add { it.addInputCharacter(c.code) }; repeat(TYPE_GAP_FRAMES) { inputQueue.add { } } }\n'+a,1)
open(p,'w').write(s)
p='src/main/kotlin/llm/slop/liquidlsd/tour/TourRunner.kt'; s=open(p).read()
a='v / 127f, deviceId = "Midi Fighter Twister"))'
if a in s: s=s.replace(a,'v / 127f, deviceId = step.param ?: "Midi Fighter Twister"))')
open(p,'w').write(s)
