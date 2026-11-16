# Open items carried over from finished plans

Made 2026-11-15 when finished plans and handoffs were deleted from `.planning/`. Each line is a loose end those
docs still listed. Four were checked against the code and dropped as done; the rest were **copied, not re-checked**, so some may since be done. Strike or delete a line when it is
settled. The source doc is in git at `4fa7646` (`git show 4fa7646:.planning/<file>`).

Things that only need a look in the running app or on hardware are tracked in `app-checklist.md`, not here.
Milestones and v1.1 features are in `ROADMAP.md`.

## Code and docs

- `rendering.md` and `ui.md` were patched item by item, never re-read end to end; the ARCHITECTURE.md "Unverified" list is unchanged. (audit-2026-10-29)
- FX queue transport commands (`fx.queue_next/prev`, `fx.bg_queue_next/prev`) have no free button in the built-in Twister profile: all six side buttons, plain and shifted, are taken. (Chain Link Part B is done: the stride-6 layout is now the default profile.) (v1-polish-handoff, midi-controller-handoff)
- Saved FX, chains and transitions have no subfolder convention, so the browser tree shows them flat. (unified-browser-pane-plan)

## Light theme

What remained of the 2026-10-07 colour audit (`theme-color-audit.md`):
- Literals still in code (re-checked 2026-11-15): `AudioEnginePanel` meters, `ParametersRenderer` fills and `(0.2)` grey, `ModulatorHeaderRow` delete button, `TempoSyncPanel` dim colour. `TourRunner` overlays stay literal by design.
- The die button and fader are copy-pasted between `MixerPanel` and `DeckControlPanel` with the same literals; move both to the existing `RANDOM_*` / `SPEED_*` / `BUTTON_*` Roles.

## Naming (needs the owner)

From the 2026-10-04 naming sweep, the decisions never made:
- Spell out BG/PV ("Background", "Preview") in menus and tooltips, or keep the short forms?
- What to call the FBO telemetry in the menu bar (GL jargon).
- Bypass vs Mute vs Enable: proposed Bypass for FX and shaders, Mute for modulators.
- "MIDI Learn" vs "Macro Binding" wording. Undo for binding edits is v1.1. (macro-editor-handoff)

## Fractal Flame

- v1.1 (decided 2026-11-16). Not built: the post stage (`post_X` at weight w gives `w * X(sum of the regular variations)`), `pre_curl`, `pre_crop`/`post_crop`. (flame-blackbox-handoff)
- v1.1 (decided 2026-11-16). Missing variations, by use in the JWildfire set: `nblur` (random), `line`, `scry2`, `epispiral`, `glynnia`, `ecollide`, `bswirl`, `sphericaln`, `julian2`, `bubble2`, `voron`, `shredlin`. Random ones need statistics in `extract_points.py`. Then re-run `curateFlames` and compare `variation-survival.csv`. (flame-blackbox-handoff)
- Quality knobs (`Points`/`Passes`/`Kernel`) as a device setting; re-measure Tier H on the RTX A2000. (flame-source-handoff)
- Owner only, recorded in the clean-room ledger: where the first AI's code description is and which program it read; what the `a7cba2b` amendments of `secant2`, `twintrian`, `cross` were based on; which beat-tracker papers to cite (BTrack, Stark, Davies and Plumbley 2009; Ellis 2007?); trademark search for Fractal Flame / Garden / Breed and a legal review of the ledger before selling. (flame-blackbox-handoff)

## Web TV and Spaz Radio

- Tapes, later steps (share links done 2026-11-16): WATCH links on spaz.org/radio/archives/ (outside this repo; they can use the `?tape=<id>&t=` links); peaks-driven scene timing (cut just ahead of a known drop); a rerun channel timed from the clock. (web-tv-tapes-plan)
- Rolled looks: 4 scenes came out too bright in the render check (build-drop-fluid, gyroid-kick, icosa-build, rd-hard); narrow their spans if they look washed out. Nobody measured how many scenes are opaque, so the background never shows behind them. (web-tv-viewer-controls-plan)
- Channels (C6) and share links (C7) from the upgrade plan; `?kiosk` (the other half of C7) is built. (web-tv-upgrade-plan)
- Gallery background ideas: a live endpoint for new uploads; music reaction (beat zoom, energy to turn speed, drop to a new photo). (spaz-gallery-bg-trial)

## Tours

- Cross-video references are plain text; the `{ch:<video>/<chapter>}` syntax is not built. (walkthrough-tour-feedback)

## Settled, kept so they are not re-argued (backlog-triage, 2026-10-12)

- Compost (v1.2, no milestone): the expanded FX chain view; the Mandala v2+ Recipe Vault; a free-knob Global row; discrete-parameter items 4 and 5 (momentary trigger buttons, controller detents); flame variations `super_shape`, `lazysusan`, `mobius`; patchable transitions/splitters and a Faceplate Designer.
- Rejected: our own clean-room Twister firmware (native mode uses the user-flashed XT hex); the Deck A single-row and one-line deck-row experiments; restoring the last Edit module on every exit (backlog item 9, option C); modular rack chassis phases 5 and 7-9.
- Library management, not doing (v1.1 or never): ratings, play counts, BPM/key analysis, Auto DJ, multiple library roots, smart crates, sortable columns.

## Left open by the 2026-11-15 audit

Report: `.planning/audit-2026-11-15.md` (lane files in `.planning/audit-2026-11-15/`).
- Still on Thread 0: `AppPreferencesStore.savePreferences` (autosave line), `MacroBankSerializer` saves, the three export-queue-as-playlist modals, reading a dropped `.lsdplay`; offline-export ffmpeg decode (L1a-11); audio device change sleeps/opens on Thread 0 (L1a-12).
- Per-frame allocation in Spout/Syphon/YUY2 input (L1a-8); web graph.js, gallery.js, delay line, FrameTransient (L6-12 rest).
- Legacy `.json` presets are not re-linked on session restore (L1a-10 rest).
- `presets/` still imports `UITheme`, `ToastOverlay`, `FileSystemManager`, `BrowseFavorites` from `ui/` (rule written as it stands; L2-15).
- Relay fix (L6-1) needs a `server/server.js` deploy to the spaz.org VPS; `streams/playing-jsonp` should be served as `text/javascript` (spaz.org admin, L6-13).
- Proposed checks not built: layering ratchet for every package (L2-24; macro/ ↛ ui/ is now enforced), no theme forks/literals (L2-25), doc identifiers exist (L2-26), thread inventory (L2-27), stock-set and profile claims (L2-28..30), `checkWebSync` in the build + WebSyncTest covering catalog.json and mandala_recipes.json (L6-15).
- Owner decisions: section E of the report (notes entry points L4-9, README themes L4-16, unused screenshots L4-20, tour re-render L4-22, web tempo golden vectors L6-14, licensing L7-1..6, 10, 11, 13, 14, and the "To confirm" ledger rows).
