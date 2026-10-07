# Theme color audit (2026-10-07)

Scope: `src/main/kotlin/llm/slop/liquidlsd/ui` (including `browser/`, `rack/`). Method: grep for `colorConvertFloat4ToU32(<literals>)`, `ImColor.rgba`, `pushStyleColor(..., r, g, b, a)`, `style.setColor`, `floatArrayOf(r, g, b)`, hex literals. About 380 literal sites outside `TangoPalette.kt` / `CvTheme.kt`; `isLight` branching appears in only 8 UI files. Nothing was run; "theme-safe" is a reading of the code against the two themes (Dark = `GREY_ACID`, Light = `ORANGE_SUNSHINE`, light is `styleColorsLight` plus overrides in `UIThemeStyler`). Verdicts: **yes** reads fine on both, **dark-only** assumes a dark surface or white ink and will be wrong or illegible on the light theme, **island** a deliberately dark panel that is fine on both if its ink is also fixed, **branched** already has an `isLight` branch but the dark side is still a literal.

Existing `TangoPalette.Role`s (`u32()` resolves per theme) cover badges, cells, pills, buttons, mode toggles, panel bg/border, crossfader parts, learn/cancel, the FX pill/cell family and `WHITE`. Entries below that already have a Role are marked "use existing".

## Already done in this pass (like-for-like, no visual change)

| file:line | change |
|---|---|
| `MixerPanel.kt:105`, `DeckControlPanel.kt:230` | `(0.08, 0.08, 0.08, 0.80)` badge pill -> `TangoPalette.PILL_BG.u32()` (identical value) |
| `MixerPanel.kt:416` | `if (isLight) X else X` with the same literal in both branches -> `TangoPalette.XF_HANDLE_BORDER.u32()` |
| `MixerPanel.kt:468` | live-dot ring `(0.1, 0.1, 0.1, 1)` -> `TangoPalette.XF_HANDLE_BORDER.u32()` |

## First light-theme pass (2026-10-07, not yet seen in the app)

Done from the audit, all via new `TangoPalette` Roles (dark value unchanged, light value added; every Role is in `ALL_ROLES` so the both-themes test covers them):
- **Overlays**: `HOVER_OVERLAY`, `PRESS_OVERLAY`, `HEADER_TINT`, `CELL_WASH`, `HOVER_BORDER` (white-alpha on dark, black-alpha on light) in `CustomIconButton`, `LinkModeButton` (also its linked/idle fills -> `BADGE_BG` / `BUTTON_SOFT_BG`), `ParameterGridHeaders` (header tint, kebab dots -> `Text`/`TextDisabled` slots) and `ParametersRenderer` (cell wash, hover fill and border).
- **Locked/disabled captions**: `TEXT_FAINT` / `TEXT_DIM` (light = `TextDisabled` slot) in CustomRangeSlider, BeatDivisionSlider, ValueParamSection, Lfo2Section, ModulatorHeaderRow.
- **Status text**: `TEXT_OK` / `TEXT_WARN` / `TEXT_ERROR` in Broadcast, MIDI and Shortcuts preferences, PopupManager, SavePresetModal, RackUnit, PresetListPanel; `QUEUE_AB_TEXT` / `QUEUE_BG_TEXT` in the five queue panels.
- **CvTheme**: `getThemeColor` / `getThemeColorRGB` scale the neon hues by 0.62 on the light theme (same hue, more contrast). One knob: `CvTheme.LIGHT_THEME_SCALE`. Per-signal shades would be better if a hue (the yellows, the mint) looks muddy.

Not done (still open below): `DropdownStyleHelper` selected text, `ParametersRenderer` remaining literals (selected/bound fills, the `0.2` grey border), `PropertiesPanel` / `AudioModulatorSection` / `ParametersTabs` grey button triples, `MenuBar` literals, `OscilloscopeDrawer`, `AudioEnginePanel`, `MacroBindingEditor` mini-slider, `TempoSyncPanel`, `UIThemeStyler` neutrals.

## Findings by area

| file:line | colors | theme-safe | suggested TangoPalette entry |
|---|---|---|---|
| `CvTheme.kt` (whole file) | Signal hues (mint, orchid, electric blue, lime, amber, coral) used as graph strokes, cell borders, button fills | dark-only for light: pale/neon hues (`VALUE` 0,.95,.72; `SEQ` .2,.95,.3; `AUDIO_HIGH`) have poor contrast on a light surface. KDoc says it is deliberately separate from Tango | Keep the file, but give it a light variant: `CvTheme.Signal(dark, light)` with the light values taken from the `.dark` shade of a Tango hue; `getThemeColor` picks per `TangoPalette.isLightTheme` |
| `CustomRangeSlider.kt:1107,1115,1153,1165` | track `(0.80,0.82,0.85)` and handle border `(0.1,0.1,0.1)` in the `isLight` branch | branched (light side literal) | `SLIDER_TRACK` / `SLIDER_HANDLE_BORDER` Roles (light literal moves in, dark = `NEUTRAL_DARK.dark`) |
| `CustomRangeSlider.kt:212,220,934,942` (also `BeatDivisionSlider.kt:317,325`, `ValueParamSection.kt:501`, `Lfo2Section.kt:84`, `ModulatorHeaderRow.kt:119`) | label/text `pushStyleColor(Text, 1,1,1, 0.25/0.4)` for "disabled/locked" captions | **dark-only**: white at 25-40% alpha on a light panel is nearly invisible | `TEXT_FAINT` / `TEXT_LOCKED` Roles with `lightSlot = ImGuiCol.TextDisabled` |
| `CustomRangeSlider.kt:415` | slider track when macro-bound: `SYNC.normal` | yes (Tango) | none |
| `ParametersRenderer.kt:60-67,149,204,215,414-425,479-482,573-582,733-742,814-827` (41 sites) | grid cell fills, borders and stripes: cyan `(0,.85,1)`, blue `(.15,.4,.6)`, dark `(.05,.15,.2)`, `(0.2,0.2,0.2)` borders, yellow `(1,.88,.2)` base tick, white 3-15% hover overlays | **island/dark-only**: white overlays (`1,1,1,0.03-0.15`) vanish on light; dark fills are islands but borders `(0.2)` are not visible against them on light | `CELL_*` family: `GRID_CELL_BG/SELECTED/TARGET/MODULATED`, `GRID_CELL_BORDER*`, `HOVER_OVERLAY` (Role with `light` = black at 6-12%), `BASE_TICK` = `ALERT.normal` |
| `ParameterGridHeaders.kt:106,132,160,207,216,218,233,237` | header tint `(1,1,1,0.08)`, text white/grey, alert dot red `(0.85,0.15,0.15)` | **dark-only** (white tint, white text) ; alert dot yes | `HOVER_OVERLAY` Role, `DANGER.normal` for the dot, `WHITE`/`BADGE_TEXT` for ink |
| `PropertiesPanel.kt:83-85,120-129,229-231,330-332` | button faces: grey `(0.15/0.25/0.35)`, amber `(0.8,0.6,0.1)`, teal `(0.1,0.5,0.4)`, orchid `(0.72,0.45,1)`, with `TangoPalette.inkFor(...)` for ink | partly: ink is computed, so legible; the greys read as black buttons on a light panel | `BUTTON_DARK_*` (use `BUTTON_BG`/`BUTTON_HOVER` Roles), amber -> `ALERT.normal/light/dark`, teal -> new `CHAMELEON.dark`-based, orchid -> `PLUM.light` |
| `AudioModulatorSection.kt:66-68,90-92`, `ParametersTabs.kt:200-202`, `ModulatorHeaderRow.kt:69` | same grey `(0.15/0.25/0.35)` button triple | dark-only on light | `BUTTON_BG` / `BUTTON_HOVER` Roles (add an `ACTIVE` role for the third value) |
| `ModulatorHeaderRow.kt:92-98`, `Lfo2Section.kt:50-56` | bypass/enable button greens `(0.1,0.6,0.2)` and ambers, reds `(0.7,0.2,0.2)` with `inkFor` | yes (ink computed) but off-palette | `ACTIVE.normal/light/dark`, `ALERT.*`, `DANGER.*` |
| `MenuBar.kt:329-334` | mode toggle: dark side `(0.10,0.52,0.72)` etc., light side already `SYNC` / `ImGui` slots | branched; duplicates `MODE_ACTIVE` / `MODE_INACTIVE` / `MODE_ACTIVE_TEXT` | use existing Roles (`MODE_ACTIVE` values differ slightly, check the look) |
| `MenuBar.kt:362-364` | "randomize all" button `(0.25,0.18,0.32)` plum with light text | dark-only | use existing `RANDOM_BG` / `RANDOM_HOVER` Roles (close, not identical) |
| `MenuBar.kt:427-431` | stats colors; light side is Tango, dark side literal | branched | `STAT_OK/WARN/BAD/DIM/FBO` Roles built from `ACTIVE`/`ALERT`/`DANGER` |
| `MenuBar.kt:546-547` | close/exit hover reds `(0.85,0.15,0.15)` | yes | `DANGER.light/normal` |
| `MixerPanel.kt:45,78-80,105-148,185-198,211,267,378-384,412-414` | master monitor black, REC badge, die button, fader, crossfader marks; many already `isLight` | island (video area) / branched (crossfader). Die button and fader are dark-only | `XF_*` Roles exist but have no `light` value: give `XF_LINE`, `XF_MARK_*` the light literals now hard-coded at lines 378-384; `RANDOM_*`, `SPEED_*` for the die |
| `DeckControlPanel.kt:184-188,265-277,340-359` | same die/fader code duplicated from `MixerPanel` | dark-only | share one helper; `RANDOM_*` Roles |
| `PerformanceMatrixPanel.kt:243` | hover `(1,1,1,0.12/0.25)` | dark-only (invisible on light) | `HOVER_OVERLAY` Role |
| `CustomIconButton.kt:23-45` | transparent button, white 10-20% hover, `(0.8)` line color | dark-only | `HOVER_OVERLAY`, `ICON_IDLE` (`lightSlot = Text`) |
| `LinkModeButton.kt:171-187` (18 sites) | white hover, dark link fills `(0.12,0.18,0.24)` / `(0.10,0.11,0.12)` | dark-only | `LINK_BG_LINKED`, `LINK_BG_IDLE` Roles, `HOVER_OVERLAY` |
| `browser/BrowserRowMoreButton.kt:45-59` | white 15-35% hover and border | dark-only | `HOVER_OVERLAY`, `BADGE_BORDER` |
| `browser/*QueueActionsPanel.kt`, `TransitionQueuePanel.kt`, `BgQueueActionsPanel.kt` `:71-92` | queue header text mint `(0.4,1,0.8)` / pink `(0.9,0.35,0.65)` | dark-only: pastel on white | `QUEUE_AB_TEXT`, `QUEUE_BG_TEXT` Roles (light = `.dark` shade of CHAMELEON / PLUM) |
| `browser/*:DragDropTarget (0,0,0,0)` (8 sites) | hides the default drag-target highlight | yes | none (transparent) |
| `browser/PresetListPanel.kt:96` | error red text `(0.95,0.4,0.4)` | marginal on light | `DANGER.light` / `DANGER.normal` per theme |
| `OscilloscopeDrawer.kt:69-75,148,213-220,392,458-460,490-514,644-694` (21 sites) | scope bg `(0.04)`, grid greys, FrameBg for sliders, playhead cyan | island (scopes are black on both themes) except FrameBg `(0.08,0.09,0.11)` input boxes (dark-only) | `SCOPE_BG`, `SCOPE_GRID_*` Roles (theme-independent); FrameBg -> `FX_CELL_BG_RGB` |
| `AudioEnginePanel.kt:141,232,255,280,369,444-520` | meters green/yellow/red `(0.2,0.85,0.35)`, `(0.95,0.8,0.2)`, `(0.95,0.25,0.25)`, bg `(0.12,0.12,0.14)`, text greys | island meters; text colors `(0.95,0.75,0.35)`, `(1,0.35,0.35)` dark-only | meters -> `ACTIVE.normal`, `ALERT.normal`, `DANGER.normal`; text -> `ALERT.dark`/`DANGER.normal` on light |
| `AudioEnginePanel.kt:232,255`, `BroadcastPreferencesPanel.kt:71`, `VideoDisplayPreferencesPanel.kt:196`, `UiLabPanel.kt:114` | slider `themeColor` literals `(0.2,0.7,0.9,0.9)` and `(0.2,0.9,0.4)` passed to `CustomRangeSlider` | partly: `PreferencesPanel.kt:442` already switches to `ORANGE` on light, these do not | one `PREF_SLIDER` Role (`SYNC.normal` dark, `ORANGE.normal` light), used by all preference panels |
| `BroadcastPreferencesPanel.kt:101-139`, `MidiPreferencesPanel.kt:74,153`, `ShortcutsPreferencesPanel.kt:59-60`, `PopupManager.kt:113`, `SavePresetModal.kt:114`, `rack/RackUnit.kt:80` | status text greens/yellows/reds/ambers (e.g. `(0.2,0.9,0.2)`, `(0.9,0.8,0.2)`, `(1,0.75,0.15)`) | dark-only: bright yellow/green text on white is unreadable | `TEXT_OK`, `TEXT_WARN`, `TEXT_ERROR` Roles (dark = bright, light = `ACTIVE.dark` / `ALERT.dark` / `DANGER.normal`; `PreferencesPanel.kt:247-352` already hand-rolls exactly this with `isLight` branches and would collapse into them) |
| `PreferencesPanel.kt:103-160,247-352,432-442` | many `isLight ? Tango : literal` pairs | branched | same `TEXT_*` / `PREF_*` Roles as above |
| `AboutModal.kt:84-86`, `UpdatePromptModal.kt:86-88` | primary action green `(0.15,0.6,0.3)` triple | yes (white text on mid green) but off-palette | `ACTIVE.dark/normal/light` with `inkFor` |
| `DeckSourcePicker.kt:90-123` | muted text `(0.65,0.65,0.70)`, slate and teal button triples | dark-only | `BUTTON_*` Roles, `TextDisabled` slot |
| `ValueParamSection.kt:140,265,313,543` ; `SeqSection.kt:139,241,417,428` | black ink on colored fills; `(0.15)` base bar; teal `(0,.6,.8,.7)` chrome | black ink is fine (fills are bright signal colors); base bar dark-only | `inkFor(...)` for ink (already the pattern), `PANEL_BG` for the bar |
| `MacroBindingEditor.kt:338-342` | binding range mini-slider: track `(0.12,0.14,0.18)`, segment cyan/grey, handle near-white, dot amber | island on dark; on light the near-white handle and dark track clash | `RANGE_TRACK`, `RANGE_SEG` (`SYNC.normal`), `RANGE_HANDLE`, `ALERT.normal` |
| `MacroKnobWidget.kt:437,471,478` | value overlay box `(0.08,0.08,0.10,0.95)` with near-white text | island (self-contained dark chip with its own ink) | `CELL_BG`-style Role, theme-independent |
| `TempoSyncPanel.kt:69,97` | grey borders `(0.5)`, `(0.35,0.35,0.40)` | marginal on light | `PANEL_BORDER`, `BEAT_IDLE_RING` Roles |
| `TooltipHelper.kt:62,69` | tooltip text `0xFFE8E8E8`, border `0xFF404040` | island (tooltip bg is set dark in both themes) but confirm | `TOOLTIP_TEXT`, `TOOLTIP_BORDER` Roles |
| `DropdownStyleHelper.kt:66` | selected item text white | dark-only | `WHITE` Role / `ImGuiCol.Text` |
| `ButtonChrome.kt:88-89` | bevel highlight/shadow white/black alpha | yes (a bevel is white-over/black-under on both) | none |
| `PerformSurface.kt:47` | `java.awt.Color(HSBtoRGB)` | n/a: builds a hue-cycled accent, not a theme color | none |
| `UIThemeStyler.kt` (41 sites) | the theme definition: both themes' ImGui style colors as literals, partly Tango-derived (`aluminium2`, `cyan`, `orange`) | n/a: this is where the themes are defined; both themes exist here | optional: move the remaining neutral literals (`WindowBg`, `PopupBg`, `MenuBarBg`) into named `TangoPalette` constants so the file reads only palette names |
| `UITheme.kt` | only unrelated hex (font ranges, resolutions) | n/a | none |

## Five most important findings

1. **White-alpha overlays and captions are invisible on the light theme.** `Text (1,1,1,0.25/0.4)` for locked/disabled captions (CustomRangeSlider, BeatDivisionSlider, ValueParamSection, Lfo2Section, ModulatorHeaderRow) and `(1,1,1,0.03-0.25)` hover/header tints (ParametersRenderer, ParameterGridHeaders, PerformanceMatrixPanel, CustomIconButton, LinkModeButton, BrowserRowMoreButton) have no light equivalent. One `HOVER_OVERLAY` Role and one `TEXT_FAINT` Role (light = `TextDisabled` slot / black alpha) would cover about 30 sites.
2. **Status text colors are bright pastels with no light variant** (Broadcast, MIDI, Shortcuts prefs, PopupManager, SavePresetModal, RackUnit, queue panel headers). `PreferencesPanel.kt:247-352` already hand-rolls the right fix with `isLight` branches; promote that to shared `TEXT_OK/WARN/ERROR` Roles and reuse it.
3. **`CvTheme` has no light variant.** Neon signal hues (mint, lime, bright amber) are the main data colors of every modulation graph and grid cell, and they are low-contrast on a light panel. Needs a per-signal light shade; the separation from Tango (kept on purpose) can stay.
4. **The parameter grid (`ParametersRenderer`, 41 literals) is the biggest unchecked surface.** Its dark cell fills and cyan borders are likely acceptable as dark islands, but the hover/stripe overlays and `(0.2)` grey borders are not; it is the first place to look at when the light theme is finally viewed.
5. **Duplicated dark-only widgets.** The die button and fader are copy-pasted between `MixerPanel` and `DeckControlPanel` with the same literals, and the grey button triple `(0.15/0.25/0.35)` repeats in 5 files; both should move to existing Roles (`RANDOM_*`, `SPEED_*`, `BUTTON_*`) so the light theme fix is one edit. `MixerPanel`'s crossfader marks have `isLight` literals that belong in the `XF_*` Roles (those Roles currently have no `light` value, so any other user of them is dark-only).

Not changed (needs a look at the real light theme first): everything except the four like-for-like replacements above.
