# Web TV upgrade plan

Started 2026-10-22. Goal: take the web TV (`web/`) from proof of concept to a real channel.
**Rule: everything on the web TV is audio responsive.** A preset or chain that only looks pretty
does not go in a rotation. A thoughtfully chosen beat-synced LFO counts as responsive (decided
2026-10-22); a free-running time animation does not.

## Decisions (2026-10-22)
- Viewer controls **lock while the desktop is broadcasting live**; they come back when it goes offline.
- Beat-synced LFO (`lfo`/`seq` on `genUnit BEAT`) counts as audio responsive.
- Audio input stays **radio only** for now (no mic / tab audio).

## State at start
- 57 presets use only 8 sources; 16 web-ported sources had no preset: aurora_ribbons, cyclic_ca,
  girih_stars, glitch_blocks, halftone_matrix, harmonograph, hyperbolic_tiling, isometric_voxels,
  kali_set, metaballs, quasicrystal, thin_film, tunnel_scenes, video_feedback, voronoi_cells, wave_ripples.
- 11 desktop sources not ported: apollonian, escape_time, kifs, mandelbox, mandelbulb, nebula_volume,
  pseudo_kleinian (single pass, LSD_COST 3); fluid_ink, lenia, reaction_diffusion (multi-pass persistent);
  fractal_flame (Kotlin genome, separate project).
- Web chains use 12 of 27 filters. The 16 desktop default chains (`web/content/fxchains/`) have no
  modulators, so they stay out of the rotation. web-trails / -drift / -chroma had no modulators.
- Autopilot hold/fade are in seconds, not bars; beat tracking is not phase-locked.
- UI: power + volume only.

## A. Content
1. Rotation gate in `web/tools/reactivity.test.mjs`: every playlist preset and every `settings.json`
   chain must move with some audio signal or with the beat.
2. Presets for the 16 unused sources (3 each: groove/beat, hard/kick, ambient) via `scripts/gen_web_presets.py`.
3. New web chains using the 15 unused filters (3d_elevation, directional_blur, faceted_glass, fluid_smear,
   gradient_map, halftone, invert, luma_displace, luma_key, mirror_sphere, neon_edge, pixelate, retro_crt,
   thermal_scanner, wave_displace).
4. Audio-reactive web versions of the 16 desktop chains; trails chains get modulation.
5. Tag-aware preset/chain pairing (no stacking a kick chain on a kick preset; GPU cost cap); shuffle bags.
6. Port the 10 ISF sources after D1.

## B. Beat-aware autopilot
1. Phase-locked beat tracking (standalone mode). Most important: every beat LFO depends on it.
2. Phrase-aligned switching: holds in bars (16/32), crossfade starts on a downbeat, fade length in beats.
3. Energy following: drops -> hard cut to high-energy preset; breakdowns -> ambient / BG up. Needs energy tags.
4. Audio-reactive transitions; all 8 transitions in rotation.
5. Silence / dropped stream -> static or "no signal", not a frozen picture.

## C. Viewer controls (locked while live)
1. CH up/down (skip/back), FX button (next chain).
2. HOLD per layer: source, FX, BG.
3. TIME knob: 8/16/32/64 bars / infinite.
4. REACT dial: scales all audio modulation depth.
5. CRT-style on-screen display: channel, preset, chain; Icecast now-playing.
6. Channels = playlists (Ambient / Bass / Hard).
7. Keyboard + touch (arrows, space, F; swipe, long press).
8. Share link `?ch=&preset=&fx=`, `?kiosk`, CRT on/off.

## D. Performance / robustness
1. Adaptive render resolution + cost tiers.
2. Precompile the next preset's shaders during the hold (no FX pop-in mid-crossfade).
3. Pause rendering when the tab is hidden; surface autoplay/audio errors.

## Order
A1+A2 -> B1+B2 -> C1-C5 -> D1+D2 -> A6 -> B3, C6.

## Progress
- 2026-10-23 A1 done: rotation gate + rate-parameter rule in reactivity.test.mjs.
- 2026-10-23 A2 done: 48 presets (batch 6 in gen_web_presets.py), 34 FG / 14 BG playlist lines.
- Found + fixed: 23 presets modulated TIME-multiplied speed params (jitter grows with uptime); 7 deaf presets
  deleted; trails chains now modulated (moved into the generator); web-drop-strobe uses invert, not video_strobe.
- Open question for desktop: the same TIME*Speed jump affects every desktop knob/modulator on a speed param.
  A proper fix is a host-integrated phase (shader change, desktop + web). Not started.
- 2026-10-23 B1 done: onset worklet + BeatTracker (autocorr tempo, period fine-tune, comb phase, split-half
  confidence) + BeatClock (forward-only anchors into BeatFlywheel). beat_check.mjs: 80-140 BPM within ~12 ms;
  174 follows at half time (prior centred on 120). Real radio locked at ~135 BPM, confidence ~0.85.
- 2026-10-23 B2 done: holds in bars (fg 16, bg 32), fades in beats, change finishes on a bar line, next preset
  built during the hold. Bar lines are the clock's own count: no downbeat detection yet (candidate for B3).
- 2026-10-23 C1-C5 done: CH rocker (+history), FX, HOLD, TIME (8-64 bars), REACT (0.25-2x on audio_*), OSD (channel,
  look, chain, Icecast now playing), beat LED, keyboard/swipe/long-press, live lock (lsd-broadcast-status now fired),
  fullscreen on the screen element. ui_check.mjs covers them. C6 (channels), C7 share/kiosk still open.
- 2026-10-23 A3+A4 done: 24 chains (batch 7 in the generator), all 27 filters used, 35 chains in rotation. No strobes
  (photosensitivity). web-elevation uses alpha-over blend and no feedback (additive/feedback burn out to white).
- 2026-10-23 D1+D2 done: adaptive render scale (cap 1080p, steps 1.0-0.4, ceiling after a bad step-up), prewarm of
  queued looks via autopilot.setPrewarm. D3 moot (rAF already pauses hidden tabs; autoplay failure shows NO SIGNAL).
- A6 waiting: the speed-bug agent (2026-10-23) is editing the desktop sources, including the 10 to port. Port after
  their commit: add the 10 to web/sync_manifest.json, `sync_web.py --apply`, then presets (heavy ones: LSD_COST 3).
- 2026-10-23 B3 done: energy.js breakdown/drop detection; calm/build look on breakdown, hard look cut on the drop.
- 2026-10-23 B5 done: no-signal snow + NO SIGNAL OSD + stream reconnect every 10 s; `?nostatic` for checks.
- 2026-10-23 B4 done (partly): all 8 transitions used, 7 in rotation + laser_drop_flash on drops. Transitions are not
  audio-modulated themselves (they last a few beats, on the bar).
