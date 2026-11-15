# Round 8 working notes (2026-11-14 night)
Test build: rt at 8a9a458 + probes 2-6 + apply_probe8 (left/right/home/end keys, `chars` action, TOUR_TIP lines with LSD_TOUR_TIPS, LSD_TOUR_PERSIST keeps prefs + session).
Runner: CASES=cases12 CASE_DIR=round8 RESULTS_DIR=round8 python3 round8/ops8.py <w> [case...]

## §1
- 16: chips (one ×4 chip on the preset row, one on Bloom), row tooltip "Loaded on Deck A, Deck B, Deck BG, Deck PV" (screenshot), Transition Queue wording on stock and saved rows. Ctrl+F then Esc: FAILED, fixed (BrowserPane: ImGui drops the field on that Esc, so isItemActive() was false; now isItemDeactivated()). After fix: Ctrl+F, Esc leaves Library in one press; with text first Esc clears and stays, second goes back.
- 23: rename keeps folder selected; delete selects parent (Saved presets). Done.
- 28: existing-tag buttons fill the field (both rows tagged), stock categories absent from Tags. Done.
- 30/60: slot Ctrl+scroll steps Pixelate > Radial blur > Kaleidoscope (starred, ★ 1/1) and stays; never lands on a saved single FX. Done (stepping by Ctrl+scroll; tooltip calls it the shortlist).
- 40: divider drag, clamp (browser keeps min), ratio + rows-icon off survive restart. Double-click reset not run.
- 41: drop between rows keeps playing marker on its item. Mixed selection: a stock source in a selection is left out at drag start ("Moving 2 presets"), so no toast; the skipped toast can't come up from the Sources tab.
- 44: keyboard Up/Down stops at the list's end; Left/Right move ImGui focus to the row ⋮ button. Arrow keys never reach the queues although presets_and_library.md says "The arrow keys and the controller's cursor step from the browser on into the queues". Controller pane step: tree > list > A/B queue (BG queue not in the cycle on Sources). Q / Shift+Q with the controller cursor in the queue: nothing. 
- 48: Save bank from Deck A writes knobpresets/tt_bank.knobpreset.json, row shown. Loading not done yet.
- 50: ARROWS key trigger: Right accepts (next item loads, crossfader moves), each press one step, Left back.
- 52: Library double-click: first vacant slot; full chain -> "slots are full, select slot" popup (code; not screenshotted); slot-bound double-click replaces that slot; FX queue step: the whole chain becomes that one effect in slot 1 (FxOps.applyItem, deliberate). Three rules; user's call.
- 55: MIDI map of Global/queueNext did nothing in the tour (cursor stayed -1); not resolved.
## §2
- 68 BG monitor opens BG pair. 70 MST pair Browse greyed tooltip "Nothing to browse here: Master levels and Clock have no list."
- 71: pair views and MST show Back/Browse/Params (+chain button on BG/Master).
- 73: Twister knob turn leaves Queues and Macros tabs up. Done.
- 76: tooltips: badge "...in the list below (click again to stop)", chain name same, slot same, monitor "Click to browse ... in the list below" (monitor tip has no "second click stops").
- 75: scope + search kept per kind (FX Saved chains "blo"; Sources Stock "ma"). Done.
- 83: Edit Esc exit keeps L2 + LFO tab selected on reopen. Restart not run.
## §3
- 87: row rects identical across normal / slot focus / bound / empty chain. Mode content only.
- 94: Transitions ◀ ▶ tooltips show "(1/3)". Done.
- 96: A/B play toggles Auto-VJ running/paused (tooltip); no advance seen in 20 s.
- 101: Clock right-click: "MIDI Learn (A/B FX Queue Next)" + OSC Learn. MST > CTRL rows right-click: OSC Learn only (MIDI learn via the MIDI column). MIDI-driven FX queue not shown working in the tour (mapping Mixer/fxQueueNext by tour midi_map and by Learn with a fake device: no step; button click works). Harness limit or bug, unresolved.
- 103: SLOTS page: FX1/FX2/FX3 badges, knob 1 slot, 2-4 params, Wet + P1/3, FX ON only on FX1; geometry same height; saved .lsdfx dropped on FX2 badge lands in slot 2 (stock filter refused, like the deck badge); FX1 page arrow leaves FX2 on P1/3.
- 105: Twister touch on FX2/FX3 rows retargets list to slot 2/3; Master Chain View rows FX1-3 + TRAN at bottom.
## §4
- 110: curated flame loads dirty=false (no prompt possible). 111: badge tooltip "Preset curated-0001, playing Fractal Flame (Deck A)".
- 114: Save As of a garden preset lands in garden/curated/ (its folder), consistent with item 26, not with 114's "goes to presets/". Item 114 wording outdated.
- 117: Deck Rotate tooltip explains (screenshot). 120: Deck Zoom visibly zooms Isometric Voxels; Wave Ripples deck changed too (under a Mirror sphere chain).
- 121: MIDI-mapped Deck A/Gain still drives Gain after 3 source changes; MST LEVELS shows only level A/B/BG/PV/Master.
- 127: A/B queue tooltips: "load the next item into Deck B (off-air now)".
## §6/§7
- 150: Edit/row chain menu items present; Save As starts in web/. Menu title said "Deck A FX FX Chain": FIXED (FxChainHeader strips trailing " FX").
- 158: FX queue arrow tooltips: "applied to whichever of Deck A/B the crossfader favours" (doesn't literally say "share one position").
- 180: slot with metaBindings [] loads with none, stays none after Save + reload.
- 182: Dry/Wet-owned slot: dragging Wet moves its Metaknob 0 -> 0.14.
## §8-§11
- 193 part: Esc cancels OSC Learn.
- 239: Audio 1/2 stacked with power + trash, no Enable; Audio 2 on leaves Audio 1 off; Audio 1 on/off leaves Audio 2; trash Audio 1 with Audio 2 on: Audio 2 stays below, Audio 1 resets to a blank default slot.
- 240: Invert above Modulation Range in Continuous and Transient, fits 1280x720; toggles invert=true.
- 248/268 part: all 8 transitions at rest (xf -1 and +1, no audio) give pixel-identical output to linear crossfade.
- 272: level fader drag with a bound knob: stays (0.70), knob follows.
- 279: KIFS Fractal Type knob drag: base continuous, evaluated value snaps 0/1 (by design).
- 281: Deck Rotate reads -180°..180°; unit values 0-100 on knobs.
