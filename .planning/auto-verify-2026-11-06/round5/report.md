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

Each stock source, effect and transition rendered twice over the same 20 s with the synth 128 track, Audio Response 100% and 0%; the difference is what the band responses add, correlated against bass changes.

- Sources: most respond clearly (correlation with bass +0.5 to +0.86). Weak or none: `fluid_ink` (no difference), `scope` and `spectrogram` (they draw the audio directly, so Audio Response doesn't apply), `lenia`, `reaction_diffusion`, `kifs`, `cyclic_ca`, `glitch_blocks`, `wave_ripples`, `video_feedback` (weak, under +0.4), `icosa_h3` and `dynamic_spiral` (tiny differences).
- Effects at their load defaults: 13 of 27 change nothing at all (identical to no effect: Color Levels, Directional Blur, Feedback, Fluid Smear, Luma Displace, Pinch/Bulge, Pixelate, Radial Blur, RGB Split, Video Strobe, Wave Displace, Faceted Glass, Bloom very nearly). With the Metaknob at 60% 11 of them come alive and track the bass; Pinch/Bulge and Faceted Glass still change nothing visible at 60% on Voronoi Cells.
- Transitions held at 50%: all 8 respond (+0.64 to +0.83); Cyber Datamosh moves even at 0%.
