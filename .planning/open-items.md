# Open items carried over from finished plans

Made 2026-11-15 when finished plans and handoffs were deleted from `.planning/`. Each line is a loose end those
docs still listed. Four were checked against the code and dropped as done; the rest were **copied, not re-checked**, so some may since be done. Strike or delete a line when it is
settled. The source doc is in git at `4fa7646` (`git show 4fa7646:.planning/<file>`).

Things that only need a look in the running app or on hardware are tracked in `app-checklist.md`, not here.
Milestones and v1.1 features are in `ROADMAP.md`.

## Code and docs

- Plain queue appends (`LibraryNavigation.enqueue`, Q) bypass `QueueOps` and push no undo (documented as such). (audit-2026-10-29)
- `rendering.md` and `ui.md` were patched item by item, never re-read end to end; the ARCHITECTURE.md "Unverified" list is unchanged. (audit-2026-10-29)
- FX queue transport commands (`fx.queue_next/prev`, `fx.bg_queue_next/prev`) have no free button in the built-in Twister profile; Chain Link Part B (free a Twister button) is the same problem. (v1-polish-handoff, midi-controller-handoff)
- Every-ISF binding audit (a v1.0 scope item). (v1-polish-handoff)
- Twister send-knob ring brightness: a ring-brightness output in `ControllerFeedback` (indicator channel, default 6), dim send knobs, full brightness elsewhere. (twister-led-midi-notes)
- Saved FX, chains and transitions have no subfolder convention, so the browser tree shows them flat. (unified-browser-pane-plan)

## Light theme

What remained of the 2026-10-07 colour audit (`theme-color-audit.md`):
- `CvTheme` has no light variant: the neon signal colours of modulation graphs and grid cells are low-contrast on light panels.
- Literals still in code: `AudioEnginePanel` meters/slider `themeColor`, `DropdownStyleHelper` selected text, the rest of the `ParametersRenderer` fills and `(0.2)` grey border, the grey button triple `(0.15/0.25/0.35)` in `PropertiesPanel` / `AudioModulatorSection` / `ParametersTabs` / `ModulatorHeaderRow`, `MenuBar`, the `MacroBindingEditor` mini-slider, `TempoSyncPanel`. `TourRunner` overlays stay literal by design.
- The die button and fader are copy-pasted between `MixerPanel` and `DeckControlPanel` with the same literals; move both to the existing `RANDOM_*` / `SPEED_*` / `BUTTON_*` Roles.

## Naming (needs the owner)

From the 2026-10-04 naming sweep, the decisions never made:
- Spell out BG/PV ("Background", "Preview") in menus and tooltips, or keep the short forms?
- What to call the FBO telemetry in the menu bar (GL jargon).
- Bypass vs Mute vs Enable: proposed Bypass for FX and shaders, Mute for modulators.
- "MIDI Learn" vs "Macro Binding" wording. Undo for binding edits is v1.1. (macro-editor-handoff)

## Fractal Flame

- Not built: the post stage (`post_X` at weight w gives `w * X(sum of the regular variations)`), `pre_curl`, `pre_crop`/`post_crop`. (flame-blackbox-handoff)
- Missing variations, by use in the JWildfire set: `nblur` (random), `line`, `scry2`, `epispiral`, `glynnia`, `ecollide`, `bswirl`, `sphericaln`, `julian2`, `bubble2`, `voron`, `shredlin`. Random ones need statistics in `extract_points.py`. Then re-run `curateFlames` and compare `variation-survival.csv`. (flame-blackbox-handoff)
- Garden Keep: UI, strategy persistence and thumbnails were open after the slice 3 backend. (flame-source-handoff)
- Quality knobs (`Points`/`Passes`/`Kernel`) as a device setting; re-measure Tier H on the RTX A2000. (flame-source-handoff)
- Owner only, recorded in the clean-room ledger: where the first AI's code description is and which program it read; what the `a7cba2b` amendments of `secant2`, `twintrian`, `cross` were based on; which beat-tracker papers to cite (BTrack, Stark, Davies and Plumbley 2009; Ellis 2007?); trademark search for Fractal Flame / Garden / Breed and a legal review of the ledger before selling. (flame-blackbox-handoff)

## Web TV and Spaz Radio

- Tapes, later steps: `/tv/?tape=<id>&t=1h12m` share links and WATCH links on spaz.org/radio/archives/; peaks-driven scene timing (cut just ahead of a known drop); a rerun channel timed from the clock. (web-tv-tapes-plan)
- Rolled looks: 4 scenes came out too bright in the render check (build-drop-fluid, gyroid-kick, icosa-build, rd-hard); narrow their spans if they look washed out. Nobody measured how many scenes are opaque, so the background never shows behind them. (web-tv-viewer-controls-plan)
- Channels (C6) and share/kiosk (C7) from the upgrade plan were still open. (web-tv-upgrade-plan)
- Gallery background: the Android app needs an absolute `https://spaz.org/tv/gallery/` base plus CORS to show the photos when it syncs; Android data use is about one 130 kB photo per 20 s; a live endpoint for new uploads; music reaction (beat zoom, energy to turn speed, drop to a new photo) as a possible step 2. (spaz-gallery-bg-trial)

## Tours

- Cross-video references are plain text; the `{ch:<video>/<chapter>}` syntax is not built. (walkthrough-tour-feedback)
- Tour mode reads `library/` from the working directory, so a curated library for the final renders needs a clean checkout or a `--library` override (not built). (walkthrough-tour-feedback)

## Settled, kept so they are not re-argued (backlog-triage, 2026-10-12)

- Compost (v1.2, no milestone): the expanded FX chain view; the Mandala v2+ Recipe Vault; a free-knob Global row; discrete-parameter items 4 and 5 (momentary trigger buttons, controller detents); flame variations `super_shape`, `lazysusan`, `mobius`; patchable transitions/splitters and a Faceplate Designer.
- Rejected: our own clean-room Twister firmware (native mode uses the user-flashed XT hex); the Deck A single-row and one-line deck-row experiments; restoring the last Edit module on every exit (backlog item 9, option C); modular rack chassis phases 5 and 7-9.
- Library management, not doing (v1.1 or never): ratings, play counts, BPM/key analysis, Auto DJ, multiple library roots, smart crates, sortable columns.
