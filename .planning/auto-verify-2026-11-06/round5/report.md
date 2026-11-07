# Scripted pass, round 5 (2026-11-07)

Throwaway test build as before (`round3/setup.sh`, now also `round5/apply_probe5.py`: hooks `macro_bind`, `macro_unbind`, `macro_set`, `midi_map`, `audit_param`). Cases in `cases7.py`, runner `ops5.py` (adds `TOUR_PARAM` and saves widget key lists). Branch `fixes/app-verify-round5`.

## Sections 7 and 8: macros, Metaknobs, learn

| Case | Result |
|---|---|
| MIDI mapping on a macro-driven parameter | suspended, reason names the knob, resumes when the binding goes |
| LFO Min bound to a knob | drag and typing leave it alone; the bound Min label opens the binding popup |
| Initial Range on a knob-driven value | moves the knob; the value follows |
| Typed knob value with Exponential, Invert, First Half | typed 20 gives 20%, same as turning there |
| Delete a modulator | a knob bound to another modulator's Depth stays on it |
| FX-bank binding in Properties | read-only with the "edit the slot's Metaknob targets" note |
| Save bank from Deck A, Apply to Deck B | deck paths remap, `Mixer/crossfade` stays |
| Linked slot's Perform knob | moves its Metaknob |
| Chain naming a missing target | loads, shows `(missing)`, no crash when turned |
| Save chain, reload | targets, ranges and invert the same |
| Ten filters auto-bound | each has at least one Metaknob target |

Test artefacts on the way: MIDI events only reach mappings when the simulated Twister is on (case names `tw_`), and Twister banks use CCs 0-63, so mappings use 100 and up. The `Mixer/Crossfader` path doesn't exist (`Mixer/crossfade` does); a bank with a bad path drops it on Apply, which is fine.

## Fixed

- The lock caption in Properties, the binding popup header and the Perform strip label showed internal property names (`[lfoMin]`, `[subdivision]`). They now use the same labels as the mapping tables (`Min`, `Speed`).

## Not done here

- Focus header Wet slider with a Metaknob on Dry/Wet, Focus Mode knob 1, focus knobs 2-4 exponential response, multi-target start position, Super Knob tap/Shift+tap (Twister), knob order after reload, Beat Division label popup, the MIDI table caption, OSC.

## Release zip run

`leak.py` has a zip mode (`LEAK_ZIP`): the release's own `run-linux.sh`, its bundled JRE, `JAVA_TOOL_OPTIONS` for the GC log and a private home. A first try confirmed the launcher starts under XWayland; it stopped itself after a minute because the laptop went onto battery.
