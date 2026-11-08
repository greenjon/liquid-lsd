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

## Real audio and MIDI paths (`pwlab.sh`, `labrun2.py`, `lab_report.py`)

A private PipeWire and WirePlumber inside their own user, network and mount namespaces, with `/dev/snd` hidden, so nothing reaches the real sound card or the desktop's PipeWire. A null sink's monitor feeds `lsd:input_1/2`; a live readout (`LSD_LIVE_PROBE`, test build only) prints signals and tempo twice a second.

- Audio arrives through the real JACK path, band signals move, no callback errors.
- **Fixed:** when the server went away (PipeWire restarted), the app never noticed: `isConnected` was just `client != null`, so the watchdog never retried and audio stayed dead until a restart. A shutdown callback now flags it; reconnect verified mid-run. The log also said "reconnect disabled" after an ordinary failed try; reworded.
- Tempo, Audio clock: `results/live_audio.txt`. Real tracks at Mixxx 129 and 122 read right; 135 wandered (89-139); 75 read 100. Synth 174 reads 140 live and 70 offline, so that's the tracker, not the live path; synth 85 reads 85 offline but 170 live.
- MIDI hardware not testable: a virtual MIDI port needs the `snd-virmidi` kernel module (root).

## PipeWire video (both directions were broken)

Found with GStreamer in the same lab. Before: the shared feed offered no format (`EnumFormat []`), so nothing could connect; the stream flag constants were wrong (`MAP_BUFFERS` had `DRIVER`'s value; `ALLOC_BUFFERS` means the app allocates, which it didn't); the `spa_data` mirror had an extra field and the sender read `spa_buffer.metas` as `datas`; no chunk size was set; the sender read framebuffer 0 (the app window) instead of the feed's texture. The receiver offered no format either, assumed 1920x1080 RGBA, and the browser passed `node.description` as `target.object`, so PipeWire fell back to whatever source it liked.

Rewritten against PipeWire 1.6's headers (fetched to check every constant): `SpaPod` (format and buffer pods, format parser, `SpaPodTest`), `SpaBufferLayout`, stream listener with `param_changed`, driver-mode sender with flipped rows, receiver accepting RGBA/BGRA/RGBx/BGRx at any size and rate, rows mirrored into GL order, discovery listing only `Video/Source` and `Stream/Output/Video` nodes and connecting by node name.

Verified end to end: GStreamer SMPTE bars (BGRx 640x360 30 fps) → app receiver → Deck B → app's Deck B feed → GStreamer capture. The app's Deck B monitor and the captured frames match and are upright. Not tested: OBS (its PipeWire sources go through portals), a real camera, DMA-BUF (not implemented; the guide no longer claims it), size changes while a reader is connected.

## Picture response (`respond.py`, `results/respond_*.txt`)

Each stock source, effect and transition rendered twice over the same 20 s with the synth 128 track, Audio Response 100% and 0%. Only the main output monitor is measured; a first run measured the whole window, so knob rings moving with the music counted, and those numbers are kept in `results/respond_uncropped/` only for the record. The figure is what the band responses add, and how closely that follows bass changes. It cannot see an effect that warps geometry the same way with and without audio.

- Sources: most clearly follow the bass (+0.5 to +0.86). Weak: Cyclic CA (+0.19), Glitch Blocks (+0.27), Video Feedback (+0.18), Wave Ripples (+0.28), KIFS (+0.06), Lenia (+0.09), Dynamic Spiral, Mandelbulb, Kali Set and Gyroid (under +0.4). None: Fluid Ink (0.85 difference). Scope and Spectrogram draw the audio themselves, so Audio Response doesn't change them.
- Effects at their load defaults: 13 leave the picture exactly as no effect does (amount 0 by design). At Metaknob 60% all of them change it and follow the bass, except Video Strobe (strobes on its own clock, -0.01). Pinch/Bulge measured the same as no effect even at 60%, but a direct frame comparison shows a clear bulge (centre difference 64, edges 4): the measure can't see it, the effect is fine.
- Transitions held at 50%: all 8 respond, most from +0.72 to +0.86; Cyber Datamosh moves a lot on its own (+0.28).
- Side finding: five bundled shaders use `smoothstep` with the edges reversed (Pinch/Bulge, Wave Displace, Chladni Cymatics, Nebula Volume, Aurora Ribbons, and their web copies). GLSL leaves that undefined; NVIDIA gives the expected result, so they work here. Not changed; worth a look if they misbehave on another GPU.

## Text and layout sweep

- Stale terms in UI strings and the guide (Reactivity, NO TARGET, Auto Gain checkbox, Headroom, BYPASS, FX1/FX2 banks, MULTI, Classic view, DMA-BUF, Column 3): the guide still said a target-less Metaknob shows `NO TARGET` (removed 2026-10-22) and the glossary called the chain switch "Bypass"; both fixed. UI strings were clean.
- Layout (`layout.py`, `results/layout.txt`): 13 views at 1280x720 and 1920x1080, every on-screen widget rectangle compared. No two widgets overlap without one holding the other. The only flags are list rows scrolled below the browser's visible area (registered but clipped), not real. A 1100-pixel window gives no data for most views (below the supported 1280x720).

## Web leftovers (`tests/web/round5.spec.ts`, `web/`)

Chromium and Firefox, local server, nothing leaves the machine: every key changes what it should (R, J, E, U, arrows, H, I, =, -, N, P, M, S, T, ?, F, C); MENU, SCHEDULE, TAPES and ? open and close with the TV off and leave it off; the nameplate opens the site in a new tab and leaves the TV on; the ABOUT QR names the site (not decoded, no decoder here); the chat blackboard fits beside the TV at 1280x720 and below it on a phone, no sideways scroll, socket blocked. While the chat box has focus, keys type into it, by design.

## Twister

Round 3's simulated Twister already covers section 14; its results are on the checklist. What's left needs the hardware.
