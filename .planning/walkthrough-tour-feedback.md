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
