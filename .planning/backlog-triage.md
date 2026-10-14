# Backlog triage (started 2026-10-12)

Goal: sort every plan that was considered and back-burnered into one of six buckets, so nothing lingers undecided.

| Bucket | Meaning |
|---|---|
| **Completed** | Built and merged to `main`. Nothing left to do. |
| **v1.0** | Assigned to the first release (feature freeze: polish, usability, stability only). |
| **v1.1** | Assigned to the release after. |
| **Compost (v1.2)** | Worth keeping on a list, but no milestone. Revisit when someone asks for it. |
| **Rejected** | Decided against. Kept here so it is not re-litigated. |
| **Decide** | The owner has not ruled yet. |

"Proposed" bucket = my recommendation from the evidence below; **the owner's call overrides**. Status was checked against `main` (git log, file existence, grep) on 2026-10-12. "Unseen" means code exists but nobody has looked at it in the running app or on hardware.

## 1. Verified status of the previously "unknown" items

| Item | Finding | Proposed bucket |
|---|---|---|
| Flame source (branch `feature/flame-curator`) | **Merged**: the branch is 0 commits ahead of `main`. 158 curated flames ship as the Garden's starting stock. The 442-flame run is superseded by that curated set. The earlier handoff text ("local only, not merged") is stale. | Completed |
| Twister native mode, `perf-mode-parity-gaps` branches | Merged (0 ahead of `main`). | Completed |
| Twister clock knob | Committed (`2d1e02b`, 2026-10-08). Still unseen on hardware. | Completed (hardware check open) |
| Twister Library send-to-deck knobs | Built and committed (`67ccf94`), later refined (`3cad2c6`: sends from Library FULL only load). | Completed |
| Dock target as a selection | Built: phases 4 and 5 committed, docs and release notes written. The old `rackBrowseTarget` plumbing is gone from the code. | Completed |
| Dock binding single source of truth | Superseded by the pair focus view. The lifecycle fixes were committed. | Completed (superseded) |
| Pair focus view | Built and committed (`NavigationSurface`, `PairKnobTouch`, page tabs close the pair view). Open items: backlog item 9 (owner decides, recommend A = leave as is), FX tap, scope memory per kind, tooltips. | Completed, 3 small tails → v1.0 |
| Perform pages | Phases 1-4 built (`PerformPagesPanel` exists). Open: hardware check, per-placement starting toggle. | Completed (hardware check → v1.0) |
| Automated screen capture / UI Lab | **Built** (`e30a75f`: `--screenshot-ui`, `--ui-lab`, `captureUiLab` task, developer docs). ROADMAP still says PARTIAL; that row is stale. | Completed (fix ROADMAP row) |
| Web preset pack batches 2-5 | **Done**: `library/presets/web` holds 57 presets (the plan's 17 + 40) and 11 FX chains. | Completed |
| Web renderer parity phase 8 (CI, docs sweep) | Not done: neither workflow in `.github/workflows` mentions the web client. | v1.0 (the plan targets v1.0) |
| Website (greenjon.com) | Generator built (`website/`, `813561d`); deployment is by hand FTP. Needs work; owner will do it last (2026-10-13). | Last item before release |
| MIDI phase 5 (profile UI) | Not built as specified. `MidiPreferencesPanel` and `MapsBrowserPanel` exist, but no copy-to-user, delete or reload flow. Needs a closer check before calling it open or partial. | Decide (v1.0 or v1.1) |
| Theme color audit | Partly done (like-for-like pass). `CvTheme` and the slider literals are still open. Light theme is unchecked. | v1.0 (two themes are in scope) |
| FX Metaknob multi-parameter linking | **Built** (verified 2026-10-13). `ISFFilter.metaBindings` is a list with link modes (Full, First Half, Second Half, Triangle, Bipolar, Invert). Right-click a parameter in `ParametersTabs` to link/unlink it with a mode. Bindings are baked into saved FX slots/chains (`FXSlotDto.metaBindings`) and restored on load. ROADMAP Milestone 4 row ("IDEA / Not Scoped") is stale. Caveat: the strip's "Retarget Metaknob" menu replaces all bindings with one, and the Dry/Wet safety net is re-added when the list is empty. | Completed (update ROADMAP; check retarget-wipes-links in the app) |
| `experiment/deck-a-single-row-src-fx`, `experiment/one-line-deck-row` | Not merged. `one-line-deck-row` has 1 commit (`8ef7d37`) that `main` lacks. Both are the same suspected dead end. | Rejected |
| Scratchpad, Localization, combo wrapper | No code exists (grep finds nothing). | see section 2 |

## 2. Everything else, sorted (proposed)

### v1.0
- Pre-release UI backlog items 9, 10-12, 19b, 25b: **decide** (waiting on the owner; see `pre-release-ui-backlog.md`).
- Web parity phase 8 (CI, docs sweep).
- Theme audit tail (`CvTheme`, slider literals, light theme check).
- MIDI phase 5 **slice 1 only** (decided 2026-10-13): Controller Profiles section, copy/delete/reload, validation problems, retitle "Active Mapping Profile" to "Learned mappings". See `midi-phase5-profile-ui-plan.md`.
- FX Metaknob editor + knob layout: **implemented 2026-10-14** (plan in `fx-metaknob-editor-plan.md`); needs a look in the running app.
- Backlog item 9: **option A, leave as is** (decided 2026-10-13).
- Hardware checks: Twister clock knob, perform pages, native mode on a fresh profile.
- Pair view tails: FX tap, scope memory per kind, tooltips.
- Discrete parameters and flame Smooth/unit changes: "unseen in app", so give them a look before release.
- Docs fix: ROADMAP rows for Metaknob linking (built), for UI Lab and Expanded FX Chain View, and `ARCHITECTURE.md`, which memory notes as stale.

### v1.1 (already named in ROADMAP)
- Keyboard as a virtual Twister (`keyboard-parity-plan.md`).
- Surface modularization, including the Stanton SCS.3m (`surface-modularization-plan.md`).
- Macro-learn completion: randomization rows, per-step grid, dropdowns (needs the shared combo wrapper first).
- Localization.
- Session Scratchpad.
- Unified Control leftovers: universal right-click Learn overlay.
- MIDI phase 5 later slices (in-app binding editor, learn-into-profile, per-device profile choice); pairs with SCS.3m support.
- OSC macro-knob learnable addresses (OSC UX Tier 1 items 2, 3, 5), pending the open question in ROADMAP.

### Compost (v1.2, no milestone)
- Expanded FX chain view (Mixxx-style 3 sub-rows). **Decided 2026-10-13: compost** (was shelved to v1.1).
- Mandala v2+ Recipe Vault.
- Global free-knob row (GLOBAL bank stays at 0 knobs).
- Discrete parameters items 4 and 5 (trigger params as momentary buttons; controller detents), unscheduled until asked.
- Flame variations still skipped: `super_shape`, `lazysusan`, `mobius` (each needs more parameters than a slot holds). `pre_blur` (67) has since been implemented.
- Patchable transitions/splitters and a freeform Faceplate Designer (both need a `Deck`/`Mixer` restructure).
- Mandala dynamic step counts and `meta.json` `steps` key (the owner already dropped these; arguably Rejected).

### Rejected
- Twister clean-room firmware (shelved 2026-10-08; native mode uses the user-flashed XT hex). Revisit only if shipping our own MIT firmware.
- Deck A single-row / one-line deck row experiments.
- Option C for backlog item 9 (restore the last Edit module on every exit; "surprising").
- Modular video rack chassis phases 5, 7-9 (already retired and removed per ROADMAP).

## 3. Open questions for the owner
1. ~~Website~~ Decided: needs work, done last.
2. ~~MIDI phase 5~~ Decided: slice 1 in v1.0, rest v1.1.
3. ~~Expanded FX chain view~~ Decided: compost. Metaknob linking is built but lacks an editor; see `fx-metaknob-editor-plan.md`.
4. ~~Backlog item 9~~ Decided: A.
