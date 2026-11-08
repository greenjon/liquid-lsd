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

## Overnight, 2026-11-08 (memory runs going alongside)

### Video sharing on Windows and macOS (code review, nothing run on those platforms)
- **Spout was impossible on Windows.** The release zips had no `SpoutLibrary.dll` (`library/*` is gitignored and no workflow fetched it), and the code called C exports the DLL doesn't have (it exports only `GetSpout`, a C++ object). New `SpoutApi` calls the object's vtable; slot numbers from SpoutLibrary.h 2.007.017 in MSVC order (overloads grouped), confirmed from the DLL's own vtable (slot 8 and 24 share a target, slot 4 fixes up a byte arg, the table ends after 171). `SpoutApiTest` checks dispatch through a fake vtable. CI now downloads the SDK zip (SHA-256 pinned) and takes the MD build, the same bytes checked; BSD-2 notice added. Sender inverts as the SDK default; receiver uses the 2.007 receive / IsUpdated loop.
- **Syphon could not have worked.** Server init used a selector Syphon lacks, `objc_msgSend` was called variadically (wrong on Apple Silicon) with NSRect/NSSize by reference, C strings went where NSStrings belong, received frames are GL_TEXTURE_RECTANGLE but were sampled as 2D, and each frame image leaked. New `SyphonBridge` uses fixed-signature calls, by-value structs, the CGL context, released images and a blit into an own texture; keys come from the framework's exported constants. Checked against the headers of both the repo copy and the Syphon 5 framework the release ships (same selectors).
- `--smoke-test` (run on all five platforms by CI before a release) now lists Spout senders on Windows and Syphon servers on macOS, so a wrong slot or call shows up there.

### Other fixes
- **Recordings ran fast below 60 fps.** One frame per render into a 60 fps file: 11 s at 30 fps became 5 s, ahead of the audio. Frames now carry their capture time and are repeated or skipped to the file's rate: 9.88 s video, 9.89 s audio for a 10 s recording at 30 fps. The guide's save folder and file name were wrong too.
- **Reversed `smoothstep` edges** (undefined GLSL) in five shaders, desktop, bundled library and web copies; renders identical before and after (Pinch/Bulge within rounding). Web copies need a publish to go live.
- Esc closes the offline render dialog.
- **OSC doubles and 64-bit ints were dropped** (`osc/OscCodec.kt`, 8f79753): only `f`/`i` were read, and any other type was skipped without reading its bytes, so later arguments came from the wrong place. `d`/`h` now drive values, `t`/`c`/`r`/`m`/`S` are read past, an unknown tag ends the message.
- **Recordings over a few minutes lost their sound; stopping froze the output** (ff4b121). The mux (aac 320k of the whole set) ran on the render thread with a 15 s limit; an hour of audio takes minutes to encode (266 s measured here under load). Lab, 5-minute recording with a tone, old build: 15.2 s render freeze at stop, file video-only (299.27 s) with "Muxing failed". Fixed build: no gap over 0.6 s, file with video 298.93 s and audio 298.92 s, `SAVING...` badge seen. Finishing now runs on its own thread, exit waits for it, a failed mux keeps the `.wav`, mux output goes to /dev/null (an unread pipe stalls ffmpeg).
- **Offline render of a two-hour mix ran out of memory** (ce148e1). `AudioDecoder` collected chunks then copied them: a 2-hour track failed with `OutOfMemoryError` at `-Xmx2g` on its own, and the frame loop has no catch, so in the app it would end the program. Now decoded through a temp file into an exact-size array: 2 hours in 2.5 s, 1.24 GB heap; samples bit-identical to before (hash checked), so tours don't change. Too long even for that: a message. Also: saves keep the file's permissions (8720017; the temp file was rw-------).
- **Big libraries made every frame slow** (7fbb952). 10,000 presets, Library open: 150.7 ms a frame (6 fps). JFR: the folder tree rebuilt each frame (`folderNodes` was O(folders x entries)), every row drawn, and the presets folder walked one to three times a frame (`checkAutoRefresh` walked every frame once 500 ms had passed). Now 7.3 ms (an empty library: 6.6 ms). Checked: keyboard down 40 rows then Enter loads that row on the clipped list, a row far down opens its menu and loads, a preset added by another program shows up, and 19 round-2 Library checks still pass. Note: tour renders keep synchronous scans; `LSD_ASYNC_SCANS` in the test build measures the real-app path.
- **Preferences > Audio could freeze the app for good** (5c4f303). Crash-test seeds 215 and 217 stopped producing frames; `replay_hang.py` reproduced 215 every time and a SIGQUIT thread dump put the render thread inside `AudioSystem.getMixerInfo()` (native ALSA probe) from `AudioEnginePanel`. The Java Sound device list is now built on its own thread (System Default meanwhile, "Scanning for devices..."). Both seeds now finish (99 s, 81 s). With no sound devices visible the probe takes 10 ms, so the stall came from this machine's real cards: the crash test only cut the network, so its Java Sound probes listed the real hardware (read-only). `monkey.py` and `replay_hang.py` now also give the app an empty `/dev/snd`. One check (probe B) also asked the real PipeWire server for its device list, read-only, about 12 ms.
- **Syphon leaked** (ab3e52a, code review, untested): no autorelease pool on the discovery thread (every 2 s) or the render thread; every Syphon call now runs in its own pool. Checklist asks for a 30-minute memory look on a Mac.
- **Playlists between Windows and Linux/macOS** (24bfa1a): subfolder entries were written with `\` on Windows and never resolved elsewhere; the presets-root check took `presets2/` as inside `presets/`. Entries now use `/`, backslash entries resolve, move and delete everywhere; 3 new tests, both move tests fail on the old code.
- **Damaged settings files** (`corrupt.py`, 33 runs: 11 state files each cut short, random bytes, empty): the app came up every time. But the MIDI/OSC profile, shader folder list, session, favorites, shortlist and notes were then saved over on the next save (the shader folder list at once), losing a hand-edited file. Now kept as `<name>.unreadable` with a toast (4896bda); checked in the real app (copies made, toast shown).
- **`--no-audio` still started audio** a few seconds in: the watchdog tried JACK / Java Sound three times. Fixed (4896bda); 0 reconnect attempts afterwards.
- **Saves could destroy the file they replaced** (6bac01c). 32 user-file writes were in place; on a full disk (private tmpfs, `cases9.py n_full_disk_save`) the old preset was emptied and replaced by cut-off JSON. All now go through `utils/AtomicFile.write` (temp file, then move; read-only targets still refused). Rerun: preset byte-for-byte intact, no temp files, toast `Could not save preset tt_alpha.lsd: No space left on device`. Round 2's read-only chain case still passes. Left as is: the deck keeps looking saved after a failed save (putting the old baseline back crosses threads; not worth the risk tonight).
- **Unsavable names were accepted** (6bac01c). `AC/DC` became `DC` inside a new folder `AC`; Rename to it did nothing silently; `CON`, `Mix: 1` can't exist on Windows. Save As, Rename, New Playlist and the five Export dialogs now show the problem live and won't confirm; the field keeps the keyboard after a refused Enter (`cases9.py`, 7 checks, screenshots).
- **Accented file names became `Caf?` under `LANG=C`** (bundled Java 17: `sun.jnu.encoding` follows the locale; `-Dsun.jnu.encoding` is ignored). `run-linux.sh` / `run-linux-arm.sh` now switch to `C.UTF-8` (or `en_US.UTF-8`) when the charmap isn't UTF-8; checked under `LANG=C`, `POSIX`, `LC_ALL=C`. macOS Java always uses UTF-8 and Windows uses wide-char file calls.

### Checked, fine
- Every stock source, effect and transition compiles on Mesa (Intel and llvmpipe): no errors. Warnings are the harmless `GL_EXT_gpu_shader4 : enable` and Mesa's per-function "used uninitialized" on globals set in `main()` (false positives).
- Real app on Xvfb: FX ON survives File > Exit and relaunch; the session (deck source, crossfader, FX states) is restored; Record Output and Render Video (Offline) produce valid 1080p H.264 with audio (offline 8.000 s for 8 s of audio); the secondary output window shows the master, upright.
- Narrow windows: no exceptions at 1100, 900 or 700 px; the Clock row drops its QUEUE block below about 1000 px.
- OSC suspension uses the same check as MIDI (covered).
- `AbletonLinkEngineTest` fails only because the local `liblink_jni.so` (gitignored, built 2026-11-05, likely from the link-follow-lead work) predates the current JNI source; `main` doesn't ship the library. Not touched.
- evdev touch backend: struct sizes and ioctl numbers match the kernel headers.
- **Broadcast, end to end** (`relay.py`, `tests/web/broadcast_replay.spec.ts`): the real app inside a private network namespace broadcast to a local relay (1074 messages, about 15/s); replayed into the web TV in Chromium it showed LIVE, the name, both decks, the crossfader, the FX chain and a picture. Relay killed for 10 s mid-show: the app retried every few seconds and was back with a fresh full state 5 s after the relay returned.
- **OSC, end to end** (`oscsend.py`): UDP into the real app, crossfader read back through the broadcast. Float, double, 64-bit int, a bundle and an XY pair's Y axis all moved it exactly; 200 random packets, a truncated message, an unknown tag and a bundle with a bad length changed nothing; 20,000 packets in 0.05 s didn't hurt, and the next move after them landed.
- UI font covers Latin-1 and Latin Extended-A only: Greek, Cyrillic or CJK names save correctly but show as `?`. Inter has Greek and Cyrillic; loading them roughly doubles the font atlas (8 sizes). Your call; not done.
- Window abuse, real app on Xvfb: 1x1, 40x30, 1x720 and 1280x1 windows, unmap/map (minimize) four times: no exceptions, and back to normal drawing at 30 fps afterwards.
- Crash test seeds 200-229 (12,373 actions): 28 clean, 2 froze (the Preferences > Audio hang above, fixed). Seeds 230-269 on the fixed build with an empty `/dev/snd`: 40 of 40 clean (16,575 actions), nothing but the damaged fixtures.
- Lab housekeeping: `pwlab.sh` never stopped its PipeWire and WirePlumber, so two earlier labs' copies were still running (each in its own namespace and runtime folder, never the desktop's) and a reused lab folder couldn't start. Stopped by PID; `pwlab.sh` now stops them when its command ends. The lab folder must be a short path: the scratchpad's is too long for PipeWire's socket.
