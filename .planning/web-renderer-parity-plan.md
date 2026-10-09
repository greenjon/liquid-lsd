# Web renderer parity plan

Started 2026-10-08. Status: Phase 0 DONE (uncommitted), phase 1 partly built (`web/isf.js`). Target: **v1.0** (user decision 2026-10-08: the web client is part of v1; phases 0-3 and the preset pack are the release scope, 4-6 as time allows).

## Goal and decisions (user)

- Web renderer predates ISF support; bring it to as much parity with the desktop as is practical.
- Once the renderer works, author 50 (or 10 first) audio-reactive `web-*` presets: beat-synced motion, bass-gated motion. Preset work is Phase 7 below and is blocked on this plan.
- Mandala stays a special case (300+ recipe lookup table, not portable to ISF). `dynamic_spiral` is plain ISF now (`TIME*Speed`), so it is generic, not special.
- Desktop stays the source of truth (`DECISIONS.md` §10). `web/` stays vanilla WebGL2 ES modules, no bundler/npm. Sync tooling is the one allowed Python.

## Findings (read from code, not yet run in a browser)

- `scripts/sync_web.py --apply` copied the ISF sources and the new `mixer.frag` into `web/shaders/`, but only swaps `#version 330 core` for `#version 300 es`. The `.frag` files are raw ISF (`/* {json} */` header, `isf_FragNormCoord`, `RENDERSIZE`, bare input names, no uniform declarations). They should not compile.
- `web/renderer.js` still has per-source hand-coded uniform tables (`programs{}` ~L232-270) and one `if (srcType === ...)` branch per source (`renderVisualSource` ~L330-505), using the old raw names (`Hue Offset`, `Max Points`).
- `renderer.js` ~L620 sets `uTex2/uMode/uBalance/uBloom`; synced `mixer.frag` wants `uTex1/uTexBG/uProgress/uLevelA/B/BG/Master`.
- `sync_web.py --check` and `WebSyncTest` only compare hashes of transpiled files; they never compile anything, so "ALL IN SYNC" says nothing about whether it runs.
- Four ISF sources are shipped but unused by the renderer: `celestial_engine`, `chladni_cymatics`, `domain_warp_fluid`, `gyroid_hyperspace`. Legacy `attractor_feedback`, `chladni`, `gyroid` exist only on the web.
- Desktop `ISFParser.buildGLSLFragmentShader()` (~L65-250) is ~180 lines of string work; it is the template for the JS wrapper.
- Bundled ISF usage is narrow: sources use only `float` inputs, no passes. Filters: 27, with `float`/`image`/`long` inputs, 4 use `PASSES` (bloom, fluid_smear, feedback, video_strobe), 3 use `PERSISTENT` (`FLOAT:true`), none use `IMPORTED`/`bool`/`color`/`point2D`/audio inputs. Transitions: 8, `startImage`/`endImage`/`progress`. `mandala` is the one non-ISF source (`meta.json` + vert/frag, 2048-point ribbon).
- Wire today (`WebPresetSerializer.kt`): evaluated values per deck keyed by lossy camelCase of display name, plus `feedback{9}`, `mixer{balance,alpha,transition}`. Web ignores `transition`. Relay (`server/server.js`) replays only the stale cached `state_full` to late joiners.
- Web evaluator differs from desktop `ModulatableParameter.evaluate()`: no range-scaled ADD, no clamp/snap, no SCALE, no followers, no LFO morph/hold, no generator mod; accepts web-only operators/waveforms (SUB/MIN/MAX/OVERRIDE, SAW...) that crash the desktop `valueOf`. Plain `0` params fall through `||` to defaults.
- Web beat clock is a free-running `bpm * t / 60`, not phase-locked. `audio_flux_*` resolves to a bass-only `trigger_onset`.
- Tooling here: node/npm, python3, Firefox 157 (no chromium, no Playwright), unknown glslang.

## Core idea

Live mode: desktop already evaluates modulators, so the web only needs a generic renderer driven by evaluated values keyed by ISF `NAME`. Standalone/autopilot mode additionally needs the preset loader plus a faithful evaluator and its own audio/beat. Build the generic renderer once; both modes feed it a "deck state" object.

## Phases

### Phase 0 - Prove and repair - DONE 2026-10-08, uncommitted
- `web/tools/shader_check.mjs` (+ `.html`): headless Firefox compiles every shipped ISF source, filter and transition through `web/isf.js`. `node web/tools/shader_check.mjs`. Needs only `firefox` + node; no npm. Result: 42/42 compile (software GL).
- `web/tools/render_check.mjs` (+ `.html`): drives the real app in an iframe, forces each source and every `web/presets/*.lsd` onto deck A, writes PNGs to `/tmp/lsd-render-check`, fails on console errors or blank frames. 15/15 render.
- Repairs: `renderer.js` now renders every ISF source generically from its header (`ISF_SOURCES` list, `lookupParam`, `applyUniforms`); mandala is the only special case. Legacy `attractor_feedback/chladni/gyroid` programs and shaders deleted. Mixer rewired to the synced `mixer.frag` (`uTex1/uTexBG/uProgress/uLevel*`) with an inline cosine crossfade into `blendFBO` standing in for the ISF transition (phase 2). The spiral integrator hack is gone. `||` fallbacks replaced with `??` so an explicit 0 survives.
- `sync_web.py` + `WebSyncTest.kt`: transpile now drops uniform initialisers (`uniform float x = 0.5;` is illegal in ES 3.00; it broke `mixer.frag`). `WebSyncTest` passes.
- Desktop shader fix: `3d_elevation.fs` compared an int input to `0.5`; changed to `>= 1` (ES has no implicit int-to-float).
- Existing 7 web presets migrated to ISF `NAME`s and normalised units (`attractor_flow` became `domain_flow`, since `attractor_feedback` no longer exists). Still plain-number format; real desktop format is phase 6.
- Gotchas learned: `MaxPoints` is raw 100..2000, not normalised. Default feedback (gain .96) blows out `hyper_slice` to near-white; tune per preset. The web `blit.vert` already carries zoom/rotZ, so 2D view parity is nearly free in phase 2.
- Not yet verified: real GPU, Chrome/Safari, mobile, and audio (render check runs with no audio and no relay).

### Phase 1 - JS ISF loader (M, 2-3 days) - single-pass wrapper DONE in `web/isf.js`; PASSES/PERSISTENT/IMPORTED/image inputs still open
Port `ISFParser.buildGLSLFragmentShader` to `web/isf.js`: header parse, uniform generation (float/long/bool/event/color/point2D/image), `isf_FragNormCoord`/`IMG_*` macros, `TIME/TIMEDELTA/FRAMEINDEX/RENDERSIZE/PASSINDEX`, `uAlpha`, ES 300 conversion (drop `#extension`, precision lines, `textureSize` macro, int/bool uniforms, implicit conversions). PASSES with `TARGET`, `PERSISTENT` ping-pong, `FLOAT`, `$WIDTH/$HEIGHT` expressions (port `DimExpr`). Skip `IMPORTED` and audio inputs for now.
Sync change: ship ISF text untouched (`sync_web.py` copies, no transpile) plus a generated catalog `web/catalog.json` (id, kind, inputs with NAME/TYPE/MIN/MAX/DEFAULT/STEP). Update `WebSyncTest` and `checkWebSync` accordingly.

### Phase 2 - Generic render graph (M-L, 3-5 days)
Replace per-source branches with: source (ISF by id, uniforms from `params[NAME]`) -> view2d zoom/rotZ (non-3D only, `is3D` flag) -> feedback (existing `feedback.frag`, 9 fb params) -> deck FX chain (3 ISF filter slots, per-slot enabled/dryWet, chain dryWet) -> transition (ISF transition, progress=(crossfade+1)/2) -> `mixer.frag` (BG + levels) -> master FX (3 slots) -> CRT post. Honor `globalAlpha`. Mandala: stays special-cased, but it must reach desktop parity: port `Mandala.kt`'s uniform computation and the `MandalaLibrary.kt` recipe table (generate a JSON table at sync time) so `Lobes` + `Recipe Select` work; today the web only takes raw `L1..L4`/`a..d`. Resolution scale knob for raymarched sources (`icosa_h3`, `hyper_slice`, `gyroid_hyperspace`) and RGBA8 fallback when float targets are missing.
Exit: all 8 sources, 27 filters, 8 transitions compile and run in Firefox.

### Phase 3 - Wire protocol v2 (M, ~2 days)
Versioned schema `v:2` with catalog hash: per deck `source`, `params{NAME:v}` (exact ISF NAME, arrays for color/point), `fb`, `view`, `alpha`, `fx[3]{id,enabled,dryWet,params}` + chain dryWet/enabled; mixer `crossfade`, levels, `transition{id,params}`, master `fx[3]`; beat anchor `{beats,bpm,t}`. Unknown ids: skip slot / fall back, never throw. Fix delta `null` semantics. Relay: merge deltas server-side so late joiners get current state. Kotlin serializer tests; update `web_subsystem.md` (currently stale: shows `integratedTime`, fixed 25 Hz).

### Phase 4 - Beat/audio parity (S-M, ~2 days)
Live: web runs a flywheel from the wire beat anchor (same BeatClock semantics, offset-smoothed for jitter); `dsp.js` beat detection only in standalone. Standalone: add real `audio_flux_*` (per-band spectral flux) to `dsp.js` instead of the bass-only onset alias. Expose `audio_amp/bass/mid/high` identically in both modes.

### Phase 5 - Evaluator port (M, ~2 days)
Rewrite `evaluator.js` to desktop semantics: ADD range scaling, `(cv+1)/2` unipolar remap, `finalCv*depth+dcOffset`, clamp + snap, ADD/MUL/SCALE only, `calculateAdvancedLFO` (morph, hold), SINE/TRIANGLE/SQUARE/RANDOM, followers (attack/decay, `followerMode`), generator mod (AM/PM/ADD), `seq` incl. hold/curve smooth, `randomizeBase` resolved at load. Generate test vectors from Kotlin and run them in node (`node --test`, dev-only). Keep accepting nothing the desktop would reject.

### Phase 6 - Autopilot loads real presets (M, 2-3 days)
Parse desktop `DeckPresetDto` (`ParameterDto`, `viewParameters`, `globalAlpha`) and `.lsdtrans`, FX from preset-independent playlist entries. `web/presets/` becomes real desktop-format `.lsd` files, so a preset authored once runs on both. Fix the `||`-zero bug by dropping that lookup style. Migrate the existing `web/presets/*.lsd` and `autopilot.js` fetch/normalize path; `normalizeDeckPreset` guesswork goes away.

### Phase 7 - Web preset pack (after Phase 2+5)
`web-*` presets (10 first, then up to 50), desktop `.lsd` format using only what the web supports. Folder: `web/presets/` for the web; desktop copies under `library/presets/web/` (note `prepareDefaultAssets` is not recursive, so defaults packaging needs a change if they ship as defaults). Designs: beat-division LFOs (1/4, 1/2, 1, 2, 4 bars), `audio_bass`-gated params (MUL of bass against a low base; ADD with followers PUNCHY/SMOOTH), `audio_flux_*` hits on feedback gain/zoom/kaleido, mid/high-driven hue and detail. Validation: a node script that decodes every file against the `ParameterDto` rules, checks NAMEs against `catalog.json`, plus the Phase 0 harness render. Add to `default.lsdplay`.

### Phase 8 - Tooling, CI, docs (M, ~2 days)
CI/Gradle task running the shader compile check over all shipped sources/filters/transitions; `WebSyncTest` updated; docs: `web_subsystem.md`, `web_broadcast.md`, both release-notes files, `DECISIONS.md` §10 edited in place.

## Not practical (documented as out of scope)
Spout/Syphon/NDI/PipeWire/video sources; MIDI/OSC/Twister/macros and `midi_cc_*` modulators (live mode gets evaluated values; standalone skips them); Ableton Link and the desktop multiband tracker; `audioFFT`/`audio` ISF inputs (possible via analyser texture, nothing bundled uses it; defer); randomization/queues/Auto-VJ (autopilot keeps its own scheduler); third-party ISF with GLSL 330-only constructs may fail on ES 300, especially mobile.

## Open questions
- (answered) Web is in v1.0; mandala stays special-cased.
- Live mode: send evaluated values only (chosen; exact, more bandwidth ~8 KB full state) vs. send modulators and evaluate on the web (not planned).
- (answered) Tooling is bare `firefox --headless` + node; no Playwright.
- Total estimate: ~3-4 weeks focused; critical path 0 -> 1 -> 2 -> 3; 5 and 6 can run in parallel after 2.
