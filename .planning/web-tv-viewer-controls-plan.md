# Web TV viewer controls: ROLL, background, effects, follow-music, channel skip

Status 2026-11-01: IMPLEMENTED (TV v13), uncommitted, unseen in a real browser. Decided: curated scenes
get random ranges in the content (not web-only jitter); EFFECTS AUTO/OFF is in; channel skip is in, and
picking a channel on the SKIPPED page tunes to it (X there restores it).

Principle: the cabinet holds things you do while watching; MENU holds set-and-forget preferences;
the keyboard reaches everything. No per-parameter controls, no fade lengths, no playlist editing.
Every new control is locked during a live broadcast, like the existing ones.

## 0. Content: ranges for the curated web-* presets and chains

Today only 39 of 129 foreground scenes (stock-* + mandalas) have random ranges; 0 of 27
backgrounds; 0 of the 35 web-* FX chains. The curated web-* `baseMin`/`baseMax` are just the
ISF range, so rolling across them would destroy the tuned look.

- In `scripts/gen_web_presets.py`, give each continuous parameter `randomizeBase: true` with
  `baseMin/baseMax = base ± 15% of the ISF span`, clamped to the ISF range. Same for FX chain slot
  parameters (not slot dryWet or the chain dryWet).
- Leave fixed: discrete inputs (STEP / VALUES / integer spans, e.g. ColorMode, Symmetries,
  PaletteMode), and a per-source exclude list for the few parameters that define the look
  (found in review). Mandala keeps RECIPE_ROLL as it is.
- `P()` passes `lo` to `_keep_swing` to decide bipolar: keep passing the ISF `lo`, not the
  narrowed one. (Modulator ADD range comes from the shader spec, `evaluator.js` evaluateParameter,
  so narrowing baseMin/baseMax does not change modulation depth. Check the desktop does the same
  before relying on it there.)
- Consequence (accepted): curated scenes now also come up slightly different every time they air.
- Check: `node --test web/tools/content.test.mjs web/tools/reactivity.test.mjs`,
  `node web/checks/render_check.mjs`; eyeball a sample of rolled scenes for broken looks and
  tighten/exclude.

## 1. ROLL (cabinet button beside FX, key D)

Rerolls the scene on air: its preset's random parameters and its FX chain's.
- Keep the raw preset dto and chain dto on the deck (`deck.raw`, `deck.chainDto`) when building.
- New values from `resolveBase` / the mandala recipe roll (`preset.js`; export a re-settle helper
  instead of duplicating `settle`).
- Continuous parameters glide from current base to new base, starting now and landing on the next
  bar line at least 2 beats away (smoothstep, driven from `tickAutopilot`). Discrete parameters
  (and Lobes / Recipe Select) snap on that bar line. Gliding matters for lenia / reaction_diffusion.
- Mutates the deck in place, so the history entry keeps the rolled version (CH back returns to it).
- No-op with a message during a crossfade? No: settle the crossfade first, as other controls do.
- OSD: `ROLL`. A scene with nothing to roll (shouldn't exist after step 0) says `NOTHING TO ROLL`.

## 2. AUTO ROLL (MENU row): OFF / 4 / 8 / 16 BARS

Rolls the scene on air every N bars of its hold, on the bar line, never during a crossfade. Most
useful with HOLD on. Default OFF; saved `lsd.autoRoll`; RESET clears it.

## 3. Background

- Key B: next background now (bg playlist order; a short dip, 2 beats, instead of bgFadeBeats).
- MENU `BACKGROUND`: ◀ current name ▶ steps prev/next.
- MENU `BG CHANGES`: WITH SCENES (2× TIME, today's behaviour) / HOLD / OFF. OFF fades the layer out
  and stops rendering deckBG (saves GPU). Saved `lsd.bgMode`; RESET restores WITH SCENES.
- A new background still gets a chain from the rotation: no separate background-FX control.
- Caveat: the background only shows where the scene is transparent (`shaders/mixer.frag`).
  Measure how many scenes are opaque; if most, say so in the row's help text.

## 4. EFFECTS (MENU row): AUTO / OFF

- OFF: `pickChain` returns null for foreground and background; strip chains from the decks on air
  (A, B, BG) at once. AUTO: give the on-air decks a chain again (as `nextFxChain`).
- F while OFF: message `EFFECTS OFF · MENU`. Transitions and the drop flash are not effects; unchanged.
- Saved `lsd.effects`; RESET restores AUTO. OSD FX line reads `FX: OFF`.

## 5. FOLLOW MUSIC (MENU row): ON / OFF

OFF skips `followSections` (breakdown → calm scene, drop cut) and the tempo weighting in
`prepareForeground`. Saved `lsd.follow`; RESET restores ON.

## 6. Channel skip

Like an old TV's channel skip: skipped channels keep their number but never come up by themselves.
- X: skip the channel on air. It changes to the next channel at once; OSD `CH 042 SKIPPED · X TO UNDO`
  (X within 5 s brings it back). X on a skipped channel restores it.
- Skipped channels drop out of auto rotation, R, CH up/down, and the breakdown/drop picks.
- Reaching one again: type its number (digits 0-9, tune after 3 digits or 1.5 s, like a remote;
  useful on its own). The OSD channel line reads `CH 042 · SKIPPED`; X restores.
- Review: MENU `SKIPPED` row, value `7 CHANNELS`; Enter opens a SKIPPED page (styled like the
  keys/schedule pages): one line per channel `CH 042  NAME`, click or Enter tunes to it (X there
  restores it). The last row is RESTORE ALL. Touch users get a MENU `SKIP THIS CHANNEL` row.
- Stored by preset name (`lsd.skipped`), so playlist renumbering doesn't move skips; names no longer
  in the playlist are ignored and pruned. At least 8 playable channels must stay unskipped.
- RESET does not clear skips (its help text says so); RESTORE ALL does.

## Keys after this

D roll · B background · X skip · 0-9 tune. All currently free in `ui.js`.

## Done means

- Code + tests: autopilot.test.mjs (roll glide/snap, auto roll timing, effects off, follow off,
  skip filtering and floor), ui_check.mjs (buttons, menu rows, keys, skip page), render_check.
- `?` keys page, cabinet tooltip for ROLL, MENU help texts, MENU button tooltip list.
- RELEASE_NOTES.md + docs/release_notes.md, docs/developer/web_subsystem.md,
  docs/user_guide/web_broadcast.md (viewer controls section).
- Bump TV_VERSION; publish with scripts/publish_web_tv.sh (watch for other sessions' web/ work).
- .planning/app-checklist.md items for what can't be checked headless.

## Open after implementation

- Rolled looks: RC_ROLL=2 sample put 7 of 316 rolls outside the render check's brightness band (luma 192-239, all a bit too bright; build-drop-fluid, gyroid-kick, icosa-build, rd-hard). Watch those four in the app; narrow their spans if they look washed.
- Background visibility: not measured how many scenes are opaque (the background never shows there).
- Real-browser check: .planning/app-checklist.md, "Viewer controls v13".
