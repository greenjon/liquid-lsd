# Lane 5: Tooltips and UI text (audit 2026-11-15)

Baseline `audit-2026-10-29` (fa4335e) to HEAD (aeb7937). Scope: `src/main/kotlin/llm/slop/liquidlsd/` (mostly `ui/`), the whole app, not only the diff.

## Checklist coverage

| Item | Result |
|---|---|
| Interactive widgets added or changed since the baseline have a tooltip | Mostly clean. Ran a script over every changed line (`git diff -U0`) for button/checkbox/slider/combo/selectable/menuItem/invisibleButton calls with no tooltip helper within the next lines, then read each hit. The new feature widgets all have tooltips: Hi-Mid band, Transient (Hits), Section bands, Record "Saving last recording...", FX queue nav, monitor click-again text, search Esc, playlist drop, LoadedMarks. The gaps are the new context-menu items (L5-6) and the dialog OK/Cancel/Create/Export buttons, which are self-labelled and follow the app-wide convention (clean). |
| Tooltips go through TooltipHelper, with no raw `ImGui.setTooltip` | Clean, except one dev-only sandbox call (L5-9). The old `LoadedMarks.kt:151` raw call is gone. |
| Composite widgets wrap the tooltip target in beginGroup/endGroup | **Fails**: 9 tooltips attach to a zero-size dummy and never show (L5-1). |
| Vocabulary follows the glossary and the banned-terms table | Banned terms: no "play queue"/"PlayQueue" in widget text, no "blend mode", no "Library of Presets", no "Alpha" fader. "Properties" appears only in code comments. Two leftovers: the dead mixer descriptions (L5-2) and the Mute/Bypass split for modulators (L5-4). Stale "Flux" (L5-3). |
| Menu, OSC and command names use the same wording for the same action | MIDI/OSC Learn menu items are now uniform: "MIDI Learn (X)", "OSC Learn (X)", "Cancel MIDI Learn", "Clear MIDI/OSC Mapping" (fixed since the last audit). GlobalCommands queue names are consistent. Leftovers: Mixer-tab row labels (L5-5), Mute vs Bypass (L5-4). |
| Tooltip text matches behaviour (ranges, units, defaults) | Checked these, all match the code: Audio band frequencies (150 Hz / 1 kHz / 1.9-5.2 kHz / 5 kHz filters), Section "~20 s" (`SectionWatch.LONG_TAU`), envelope presets (`AudioFollowerMode` ms values), Master BPM 40-240, Seq Length 1-32, Broadcast 5-60 Hz, 100% level reset, Garden path, the Skip button text (the old "Veto" text is fixed), MIDI mapping row ranges. Mismatches: L5-2, L5-3, L5-7. |
| Spot-check older panels | Done (L5-8). The previous audit's list is partly fixed: MIDI per-mapping row and Show/Reset order now have tooltips. Still missing: OSC per-mapping row, MIDI/OSC profile management, Video Export settings, and others. |

## Findings

### L5-1 [bug] Descriptive tooltips after `drawCompactSlider`/`drawCustomRangeSlider` never show
- Where: `ui/CustomRangeSlider.kt:1322-1323` (the slider ends with `setCursorScreenPos` + `ImGui.dummy(0f, 0f)`). Affected callers, each `itemTooltip(...)` directly after the slider with no group:
  - `ui/AudioEnginePanel.kt:242` Input Trim
  - `ui/AudioEnginePanel.kt:266` System Volume (this was the last audit's "missing" fix)
  - `ui/AudioEnginePanel.kt:330` Audio Response (`:347`, the only reactivity control)
  - `ui/TempoSyncPanel.kt:206` Master BPM (the last audit's fix)
  - `ui/SeqSection.kt:202` Length, `:232` Phase, `:563` Slope/hold, `:649` Offset
  - `ui/BroadcastPreferencesPanel.kt:78` Rate Limit
- Evidence: `itemTooltip` checks `ImGui.isItemHovered()` on the last item. Here the last item is a 0x0 dummy, and ImGui never hovers an empty rect. The slider's own label and track tooltips still show (name and default), so the bug looks like "it has a tooltip", but the explanatory text is dead.
- Impact: users never see the Audio Response, Input Trim, System Volume or BPM explanations. These are the texts the previous audits wrote for setup problems.
- Fix: in each caller, wrap the slider in `ImGui.beginGroup()`/`endGroup()` before the `itemTooltip`. Better: add a `tooltip: String?` parameter to `drawCustomRangeSlider` and show it in the slider's own label and track tooltip (that also avoids two tooltips competing on the track). Worth a check: "itemTooltip directly after a function that ends in `dummy(0f,0f)`".
- Verified:

### L5-2 [bug] Mixer-tab parameter descriptions are unreachable, and their text is stale
- Where: `ui/ParametersRenderer.kt:203-221` (sourceId derivation), `rendering/SourceDocRegistry.kt:853-870` (`mixer/...` entries)
- Evidence: for the key `"Mixer/queuePrev"`, `sourceId = keyParts[1].lowercase()` gives `"queueprev"`, not `"mixer"`. So the `"mixer" -> getMixerParamDescription` branch never runs, and `getParamDescription("queueprev", label)` returns "". Every row on the Mixer Edit tab (`ParametersTabs.kt:355-374`: queue prev/next, tap tempo, Randomize deck X, crossfade, xfadeSpeed) shows range and default but no description. The dead texts are also wrong: "steps the play queue" (banned term, should be A/B Queue), "background queue" / "background FX queue" (should be BG Queue / BG FX Queue), "A/B FX queue ... by one chain" (an item can be a single effect that replaces the chain), "randomises all modulation values on Deck A" (it randomizes parameters and modulators, effects included, per `PerformanceClockTriggers.kt:121`), and xfadeSpeed "when queue playback is active" (it also drives the AUTO button). The transQueuePrev/Next rows have no entry at all.
- Impact: the Mixer tab rows are the MIDI/OSC-learn surface for the global triggers, and they show no help text.
- Fix: when `keyParts[0] == "Mixer"`, set sourceId to `"mixer"` and paramName to `keyParts[1]`. Then rewrite the `mixer/*` descriptions in glossary wording (reuse the GlobalCommands / PerformanceClockTriggers texts) and add transQueuePrev/Next.
- Verified:

### L5-3 [wrong] Audio Response and engine-off texts list "Flux" and omit Hi-Mid
- Where: `ui/AudioEnginePanel.kt:347`, `ui/AudioEnginePanel.kt:157`
- Evidence: "scales the Amp, Bass, Mid, High and Flux signals". The code (`audio/AudioEngine.kt:795-815`) scales Amp, Bass, Mid, High, **Hi-Mid** and all five hit signals. The audio_flux_* signals are now labelled "... Hits" in the UI ("Full Mix Hits", "Kick Hits", the Transient (Hits) toggle), so "Flux" is the old name. The `:157` banner says the same.
- Impact: users looking for "Flux" signals won't find them, and they won't know that Hi-Mid is scaled too. (The `:347` text is also hidden by L5-1.)
- Fix: "scales the level signals (Full Mix, Bass, Mid, Hi-Mid, High) and the Hits signals". Add the "Flux" UI label to the banned terms (proposal below).
- Verified:

### L5-4 [polish] Modulator on/off uses Bypass in some places and Mute in others, against the glossary
- Where: `ui/ModulatorHeaderRow.kt:105` "Bypass this modulator" / "Turn this modulator on"; `ui/ParametersRenderer.kt:568` MIDI cell "Middle-click to toggle bypass." against `:600` menu "Mute MIDI Modulator"; `ui/Lfo2Section.kt:77` "Mute LFO 2"; `ui/ParametersRenderer.kt:271,765` "Mute all modulators" / "Mute Modulator(s)"
- Evidence: the glossary (`your_workspace.md`, FX ON / Bypass / Mute) says "Mute turns off a modulator" and "a slot's power icon bypasses one effect". The modulator power button and the MIDI cell say "bypass". The last audit proposed "Bypass this modulator", which the glossary later ruled out.
- Impact: the same action has two names in the same panel.
- Fix: "Mute this modulator" / "Unmute this modulator", and "Middle-click to mute or unmute." Optional: `FxChainHeader.kt:616` "Click to bypass it" could read "Click to turn the chain off" to match "FX ON".
- Verified:

### L5-5 [polish] Mixer-tab row labels don't match the queue names used elsewhere
- Where: `ui/ParametersTabs.kt:355-364`
- Evidence: "Queue prev", "BG queue prev", "Trans queue prev", "A/B FX queue prev". Elsewhere: "A/B Queue", "BG Queue", "Transition Queue Prev" (`PerformanceTransitionsControls.kt` menus, `control/GlobalCommands.kt:30-41`), and "A/B FX Queue Prev" (`PerformanceClockTriggers.kt`). These labels also show up as the MIDI/OSC mapping "Parameter" names.
- Impact: the same trigger has a different name in Edit, in the Perform menus and in the mapping tables.
- Fix: "A/B Queue Prev", "BG Queue Prev", "Transition Queue Prev", "A/B FX Queue Prev", "BG FX Queue Prev", "Tap Tempo", "Randomize Deck A".
- Verified:

### L5-6 [polish] New browser context-menu items have no tooltips
- Where (added or changed since the baseline):
  - `ui/browser/PlaylistMenu.kt:21` "Add to '<playlist>'", `:34` playlist entries, `:44` "New Playlist... / New Playlist Here..."
  - `ui/browser/TransitionBrowserPanel.kt:167` "Add N to Transition Queue", `:205` "Add to Transition Queue"
  - `ui/browser/BrowserPane.kt:585` "Add Playlist to Transition Queue"
- Evidence: the blueprint asks for a tooltip on every menuItem that has an action. Most browser and row context menus (`BrowserPane.kt:424-604`, `PresetListPanel.kt:292-351`) have none either, while MenuBar items do. The convention is mixed.
- Impact: low. The labels mostly explain themselves; "New Playlist Here" (which folder?) is the one that could use a line.
- Fix: decide whether self-describing context-menu items are exempt (then say so in the blueprint). Otherwise add one-line tooltips, starting with PlaylistMenu.
- Verified:

### L5-7 [wrong] Shift+Q shortcut description is stale; Q's "selection" claim is wrong for Transitions
- Where: `ui/shortcuts/ShortcutManager.kt:50` (library.queue_bg), `:46`-ish (library.queue_ab); behaviour in `ui/LibraryNavigation.kt:187-206` and `ui/browser/BrowserDock.kt:203-208`
- Evidence: Shift+Q reads "Appends selected preset to the BG Queue (BG)", but on the FX tab it appends to the **BG FX Queue** (`LibraryNavigation.enqueue`: FX → `FX_BG`), and it appends the selection or the tree playlist, not just one preset. Q's new text says "Appends the selection", but the `viewMode == TRANS` path in `BrowserDock.kt:205` appends only `activeFile`.
- Impact: the Shortcuts preferences page and the Q-key hint mislead users.
- Fix: Shift+Q: "Appends the selection to the current tab's background queue: the BG Queue (Sources) or the BG FX Queue (FX)." Either route the TRANS branch through `LibraryNavigation.enqueue`, or describe it as the cursor row.
- Verified:

### L5-8 [polish] Older panels still missing tooltips (spot check)
- Where:
  - OSC prefs per-mapping row: `ui/OscPreferencesPanel.kt:299,303` Min/Max, `:314` Invert, `:322` Slew, `:333` Takeover, `:339` delete. Profile bar: `:106` combo, `:113` Save, `:119` Delete, `:130` Create Profile, `:225` Clear All. (The MIDI equivalents got tooltips; copy the texts from `MidiPreferencesPanel.kt:600-680`.)
  - MIDI prefs: `ui/MidiPreferencesPanel.kt:105,108` add-binding modifier/input combos, `:111` "Choose command...", `:130` Learn, `:242` Open Folder, `:275` "Use profile", `:317` Enable MIDI Subsystem, `:330` Rescan Controllers, `:370` profile combo, `:377/:383/:394` Save/Delete/Create Profile
  - Video Export: `ui/VideoExportModal.kt:162,177,186,196` Browse/Clear, `:212,217,219,224` Resolution/Framerate/Codec/Super-sampling, `:244` Start Export
  - Preferences: `ui/PreferencesPanel.kt:410` Startup Behavior, `:379` View Update. `ui/VideoDisplayPreferencesPanel.kt:110` output enable, `:212` Recording Framerate. `ui/ShaderLocationsPreferencesPanel.kt:80` folder enable. `ui/BroadcastPreferencesPanel.kt:159` Go Live has a tooltip only while disabled, and that tooltip says "must both be configured in Preferences" although the user is already in Preferences.
  - Edit panels: `ui/ParametersRenderer.kt:1035` group dice button. `ui/FXChainMacroStrip.kt:128,132` param-page ◀ ▶. `ui/ValueParamSection.kt:122` "Add Base Value as Target", `:243` max-points pills, `:281` lobe pills, `:315,333` recipe ◀ ▶, `:354` Choice combo. `ui/StructuredEditorPanel.kt:60` flame editor tabs, `:65` add-tab button, `:85` named-choice combo. `ui/SeqSection.kt:324-325,338` Euclid Hits/Rotate/Apply. `ui/ParameterGridHeaders.kt:276,299,316,333,359` column checkboxes and "Turn On Needed Columns". `ui/MidiModulatorSection.kt:66` Clear MIDI Modulator. `ui/PropertiesPanel.kt:233` cancel-learn button.
  - Other: `ui/PerformPagesPanel.kt:140` row combo. `ui/PopupManager.kt:184` "Don't ask again", `:206` Restore Defaults. `ui/BeatPresetPills.kt:36` beat pills. `ui/MissingItemsPanel.kt:30,39` Locate/Dismiss All.
- Evidence: a script plus reading; none of these has a tooltip helper call after the item (within 40 lines, before the next widget).
- Impact: Preferences and editor users get no hover help for these controls; the OSC/MIDI gaps are the most visible.
- Fix: one batch of one-line `itemTooltip`s, OSC row first (copy from MIDI).
- Verified:

### L5-9 [polish] Raw `ImGui.setTooltip` in the UI Lab sandbox
- Where: `ui/UiLabPanel.kt:178`
- Evidence: the only raw call outside TooltipHelper. The panel is reachable only with the `--ui-lab` CLI flag (`cli/CliArgs.kt:75`).
- Impact: none for users. It would trip the planned `scripts/audit/raw_tooltips.py`.
- Fix: switch it to `itemTooltip(label)`, or allow-list UiLabPanel in the script.
- Verified:

## Proposed banned-terms additions

| Banned | Use | Seen in this audit |
|---|---|---|
| Flux (audio signal name in UI text) | Hits | `AudioEnginePanel.kt:157,347` |
| background queue / background FX queue | BG Queue / BG FX Queue | `SourceDocRegistry.kt:859-865` |
| Trans queue | Transition Queue | `ParametersTabs.kt:359-360` |
| Bypass (for a modulator) | Mute | `ModulatorHeaderRow.kt:105`, `ParametersRenderer.kt:568` |
| Deep Edit (in UI text) | Edit | comments only today; ban it before it comes back into strings |

## Proposed automatic checks (for F)

- `ui_terms.py`: add the rows above.
- A new check for L5-1: flag an `itemTooltip`/`controlTooltip` call that directly follows a call to `drawCompactSlider`, `drawCustomRangeSlider` or `drawBeatDivisionSlider` with no `beginGroup` before it.
- `raw_tooltips.py`: allow-list `UiLabPanel.kt`, or fix it.
