# Walkthrough tour: review feedback and chapter roadmap

Scripts: `tours/<video>.json` (`overview.json`, `modulation.json`). Draft render: `scripts/render_tour.sh --draft --all tours/draft --tour-hidden` (about 5 min for the overview).
Format and checks: `docs/developer/tour_renderer.md`.

## Feedback round 1 (2026-10-17): DONE in 01ff803 / 77db25e
Slow down by half; opaque caption box near the cursor; drop the Quasicrystal and Kali Set captions; show the Edit view
with "Ch. N at m:ss" references; knobs turn with up/down AND left/right; show both crossfaders (output slider and the
Transitions row XFADE knob); every deck plus the master has a 3-slot FX chain; explain left-to-right flow and why order
matters; show adding/removing slots from the Super Knob. Draft mode for fast iteration (30 fps, 960x540, NVENC).

## Feedback round 2 (2026-10-18): APPLIED 2026-10-18 (no more round 2 feedback coming)
Notes: Ch. 1 now starts deck A on Icosa H3 (draws only part of the frame) with its FX chain bypassed, raises BG
(Aurora ribbons, FX cleared) with the BG preview fader so it shows around A; Ch. 2 clicks BYPASS to turn A's chain on.
Preview faders were dead (Mixer macro overwrote them); user fixed that in 90faa25. Source scan for BG show-through:
with no FX on A, icosa_h3 and mandala let BG through clearly; through A's tunnel chain only icosa_h3 does.
1. **Ch. 1, four decks: say what each is for.** Roughly: A and B are mixed together by the crossfader. BG is the
   "background": it appears behind A and B. PV is for previewing; it is never shown to the audience.
   (Current caption only says "a preview of each of the four decks: A, B, BG and PV". Check wording against the user
   guide before writing; one caption per deck, cursor on each preview, is probably clearest.)
2. **Ch. 2, two rows per deck: name both rows up front.** Something like: "Every deck has two rows, one for the
   source and one for its FX. We'll cover sources first, then come back to FX." Then the SRC drill-down. Point at
   both rows when saying it (move to the SRC badge, then the FX row) so the pairing is visible.
3. **Ch. 2, loading a source: show it with the cursor, not a scripted load.** Today the source changes via
   `load_source` while the cursor sits still, so the cause is invisible. Instead: open Browse from the source badge,
   move to a source in the list, click it, watch the deck change; repeat for 2-3 sources so it can't be missed.
   Needs: widget keys (or stable rects) for browser list rows (e.g. `browser_row_<kind>_<name>`), and checking that a
   single click loads (vs. double-click / drag) in the bound Browse; verify determinism of click-triggered loads
   (handoff gap 7: only lightly tested, so run `tour_determinism_check.sh` on it).

## Decision (2026-10-18): overview + deep-dive videos
One overview (6-7 min is fine, no hard limit) plus separate deep dives, each on the user-guide page it explains;
the interactive tour page indexes chapters across all videos. Every video is its own deterministic script, so a UI
change re-renders only the affected video.
Lineup: 1 Overview (current tour, trimmed: Edit-view detail and some Super Knob detail move to deep dives; end with
"where to go next"); 2 Modulation (LFO, Audio, Step; split into three if long); 3 FX chains (order, Super Knob /
Metaknob, focus mode, master FX); 4 Mixing and transitions (crossfader, transitions, AUTO/fade time, clock);
5 Library and presets (browse, save, queues, pair focus); 6 Hardware (MIDI learn, Twister, OSC; filmed insert or
graphic for the Twister); 7 Output (second display, recording, web broadcast).
Tooling: DONE `tours/` with one script per video, `render_tour.sh --all`. OPEN: cross-video references (`{ch:<video>/<chapter>}` -> "the Modulation video", a link on the web);
maybe a shared setup include later.

## Chapter roadmap (overview; most items below move to the deep dives per the decision above)
Current tour (about 5:10): 1 The window, 2 Decks and sources, 3 Knobs and crossfaders, 4 FX chains, 5 Edit view,
6 Library and themes (also closes with the GPL line).

Still to add, from the storyboard in `walkthrough-video-plan.md` (order to be decided):
- **Cold open** (10 s): full-screen visuals reacting to music, no UI, title card.
- **Modulation**: LFO / Seq / Audio sources, click-to-learn binding, depth, BEAT vs TIME, target arcs on knobs, the
  macro strip in Edit. SEQ and MIDI columns are off by default: enable them first (Preferences > General).
  Audio reactivity needs the real track for a convincing shot (placeholder beat works mechanically).
- **Transitions in depth**: picking a transition, AUTO and the fade time, A/B buttons (Ch. 3 only shows the crossfader).
- **Library and presets in depth**: send to deck, pair focus view, saving presets, queues/playlists, Macros tab.
- **Hardware**: MIDI learn (right-click a knob), Twister native mode, OSC. Real input, LEDs and rings can't be
  rendered: show Preferences panels and on-screen rings; Twister needs a filmed insert or graphic.
- **Output and recording**: second-display output and Output Monitor menu, recording, Web broadcast (Preferences
  panels only; the actual second screen and relay can't be shown).
- **Clock**: BPM, TAP, MAN/AUDIO, resync (the CLK row on MIXER).
- **Close**: themes (exists), download link, GPL-3.0 (exists).

Captions: avoid generator counts; "three pages A/B, BG/PV, MIXER"; re-read the user guide before writing each chapter.

## Modulation deep dive (2026-10-18): first draft, awaiting review
`tours/modulation.json`, about 3:50, five chapters: grid (Edit via the gear, rows/VAL/columns, SEQ turned on from the
grid menu), LFO on Zoom (power, shapes, range typed, Time to Beat, Beat Div, cell knob), audio on Gain (Bass, Transient,
Punchy, range 0-40), step sequencer on Lobes (8 steps, typed values, LOBES knob steps, BEAT clock, Step Hold/Glide),
muting and combining (power mutes, ADD/MUL/SCALE, base value, same grid everywhere, saved with the preset).
Choices: deck A = Mandala with FX cleared. Rotate Z and Hue Offset barely show on Mandala (rotationally symmetric,
palette-driven), so the LFO is on Zoom. Not covered (candidates for later or for the macro video): LFO 2, Advanced
Parameters, randomize dice, copy/paste cells, middle-click mute, MIDI column, macro strip / click-to-learn.
Mechanics: new `type`/`key`/`scroll` actions, `ox`/`oy` for combo items; Escape in Edit collapses the rack, so close
combos by picking an item and popups by clicking empty space.

## Remaining deep dives (2026-10-18): first drafts, awaiting review
All five rendered as drafts (`render_tour.sh --draft <script> tours/draft/<name> --tour-hidden`), no `TOUR_WARN`, frames
checked. Every load, swap, fade and setting is done by clicking the real UI, except the setup lines at t=0.
- **FX chains** (`tours/fx_chains.json`, about 4:45, six chapters): building a chain from empty (click a slot, click an
  effect in the bound Browse; deck A starts with `fx_new` so the name reads Untitled), mouse wheel shortlist, order and
  swap by drag, slot power and chain BYPASS, Metaknob, Super Knob with soft pickup, slot link and chain link, focus mode
  (pill, Metaknob on knob 1, parameters on 2-4, page arrows, Wet slider, switching and leaving), saved chains from the
  chain name (Hyperspace Trip, Cyberpunk 1984), Save/menu, master FX (Invert in a master slot stays on through the
  crossfade). Source is Voronoi Cells: the only one in a probe of eight where Kaleidoscope, Polar Tunnel and their swap
  all read clearly (icosa_h3 went black when swapped).
- **Mixing and transitions** (`tours/mixing.json`, about 3:50): levels (LEVEL A fade, preview fader mirrors the knob,
  LEVEL BG brings BG in around icosa_h3, MASTER blackout), crossfader slider and XFADE knob, A/B hard cuts, transition
  picker (Vortex Swirl, Film Burn; Luminous Flash's flare did not read in sampled frames), knobs 2-4 as transition
  settings, AUTO and fade time (wheel to 3.0 s; a 20 px drag on the badge only moved it 0.1 s), clock (/2 and x2 with a
  Beat LFO on deck A's Morph so the change shows, TAP x4 at 120 BPM, nudge, RESYNC, MAN/AUDIO). The transition die is
  only drawn with randomization on, so it is not shown.
- **Library and presets** (`tours/library.json`, about 2:50): Space for the full Library, tabs, folders, search (typed,
  then cleared with Ctrl+A, Backspace), double-click loads into the off-air deck, drag a row onto the PV preview, source
  badge = per-deck list, star for unsaved changes, Save opens the Save Preset dialog (cancelled on screen, captioned
  as such: a real save would write into `library/presets/`), queues (search "mandala", select, Q three times, Queues tab,
  the deck row's next arrow loads and fades), pair view from a preview click, Parameters, Back.
- **Hardware** (`tours/hardware.json`, about 2:00): right-click learn ring on a knob and cancel, buttons learn too,
  Twister = the 16 on-screen knobs with pages following the side button (on-screen only), Preferences MIDI Controls
  (Perform Pages), OSC Controls (server, ports, Learn, profiles).
- **Output** (`tours/output.json`, about 2:00): Output View (new `output_view` action; F is a GLFW callback the tour
  ignores), Output menu (Secondary Output Window, Output Monitor; hovered, not clicked), Video & Display (resolution,
  scaling, Live Video Sharing, recording settings), Record Output and Render Video (hovered), Web Broadcast panel.
Open questions for the review:
1. **Library contents are part of every video.** Saved presets on screen are dev leftovers (`3d spiral test`,
   `mandala-7`, `[!]` badges, `web-*` presets, the `feedback, probably too much of it` and `fx_chain` chains). The
   Library and FX videos need a curated library for the final render; tour mode still reads `library/` relative to the
   working directory, so that means either a clean checkout or a `--library` override (not built).
2. **Hardware video**: MIDI and OSC are off in tour mode, so the MIDI panel shows "Enable MIDI Subsystem" unticked and no
   sniffer or mapping table, and OSC says the server is stopped. Options: accept (captions say "with MIDI on, this panel
   also shows..."), a tour-only fake device list, or a filmed insert. The Twister itself still needs a filmed insert or
   graphic (chapter 2 is captioned to sit over one).
3. **Output video** shows the recording folder default `/home/<user>/Videos/liquid-lsd` (a caption box covers it in the
   draft by luck; a fixed tour value would be safer).
4. Cross-video references are still plain text ("the Modulation video", "the user guide's Output page"); the
   `{ch:<video>/<chapter>}` syntax is not built.
5. Overview trim (Edit-view and Super Knob detail now covered by the FX video) not done yet.
Mechanics learned: a second drag on the same control needs a `move` back onto it first (a drag ends where the cursor
stopped, and a press off the control grabs nothing); ImGui sliders jump to the click point, so aim with `ox`; with a menu
open, moving across the menu bar switches menus, so enter the menu straight down before moving sideways; the Save Preset
modal ignores Escape.

## Pipeline intro (2026-10-19): BUILT, awaiting review
New first overview chapter `pipeline` ("How it fits together", about 2 minutes): live schematic of the pipeline, then
generator / effect / transition / master parameters, then the diagram shrinks into a corner mini-map. Every chapter of
every video now starts with the mini-map lighting its part of the pipeline (`minimap` on chapter steps). Plan,
storyboard and decisions in `.planning/pipeline-intro-plan.md`; actions in `docs/developer/tour_renderer.md`.
Ch. 1's own setup steps were removed (the intro leaves the same state). To review: pacing, caption placement, the
mini-map corner (bottom-left, over the Library list), and trimming Ch. 1's deck-purpose captions now that the intro
covers them.
