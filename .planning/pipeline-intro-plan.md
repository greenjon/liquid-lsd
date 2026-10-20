# Pipeline intro for the walkthrough: plan

Status 2026-10-19: steps 1-5 built and draft-rendered; mini-map on every chapter of every video (step 6, part 2) done; awaiting review. Ch. 1 caption trim (step 6, part 1) not done. Branch `feat/tour-phase3`.

## Goal

Before the overview shows any UI, draw the video pipeline as a live schematic, then use it to teach the app's one big idea:
**every generator, effect and transition is a node with its own parameters, and every parameter can be controlled the same ways.**
The tour then introduces the app in passes, each with more detail:

1. **The pipe** (this intro): decks → FX chains → transition → BG behind → master FX → audience; PV off to the side.
2. **Everything has parameters** (this intro, second half): generator, effect, transition, master, each shown faster than the last.
3. **Where it lives** (the existing overview chapters), then **how deep it goes** (the deep dives).

A small version of the diagram comes back as a corner mini-map at chapter starts, lighting up the part being discussed.

## Placement

A new first chapter of `tours/overview.json`, id `pipeline`, title "How it fits together", about 110 s. It is the hero
video's opening, and the tour page gets it as chapter 1 with its own clip. Burned-in captions as for the rest of the overview.

## Facts the script relies on (checked 2026-10-19)

- BG is composited **under** the A/B transition output by alpha (`shaders/mixer.frag`: `fg.rgb + bg.rgb * (1 - fg.a)`):
  BG shows wherever the A/B mix is transparent; opaque black hides it. Deck levels scale alpha too.
- Master FX runs after the BG composite (`Renderer.kt`, pass 3).
- Every deck (A, B, BG, PV) has a 3-slot FX chain; Master FX is a fourth 3-slot chain. Feedback is just an effect.
- Decks take generators (ISF, plus native) or live video over Spout / Syphon / PipeWire. **Cameras are not mentioned**
  (Linux camera input is broken, see ROADMAP "Linux Camera Input").
- Wording agreed: "Three decks reach the audience; a fourth is your private preview."

## Storyboard and caption drafts

Reading pace is 14 characters per second (the renderer's lint), so each caption's minimum time is noted. Times are
rough; the draft render decides. Diagram actions are in the next section.

### Part 1: the pipe (about 40 s)

Starting state: A = Icosa H3 (draws only part of the frame, so BG shows around it), B = Mandala, BG = Aurora Ribbons
(FX cleared), PV = Harmonograph, as Ch. 1 already sets up. The diagram is drawn over an opaque backdrop, so the UI is hidden.

| # | On screen | Caption | min s |
|---|---|---|---|
| 1 | Three deck boxes fade in, live thumbnails, labelled A, B, BG | "Liquid LSD plays up to three video sources at once, each on its own deck: A, B and BG." | 6.1 |
| 2 | An FX1-FX2-FX3 chain slides out of each deck | "Every deck runs its video through a chain of three effects." | 4.2 |
| 3 | Trans node appears, wires from A and B; crossfade sweeps A → B and back, Trans thumbnail follows | "A transition mixes Deck A and Deck B. The crossfader moves between them." | 5.1 |
| 4 | "Behind" node after Trans, BG wire joins from below; its thumbnail shows BG filling the transparent parts | "Deck BG fills in behind them, wherever their mix is transparent." | 4.6 |
| 5 | Master FX chain and an Output box ("audience") appear | "The mix then runs through three master effects, and out to your audience." | 5.3 |
| 6 | PV row appears below a divider, not wired to anything, with an "only you see this" tag | "Three decks reach the audience. A fourth, Deck PV, is your private preview, with its own effects." | 7.0 |

### Part 2: everything has parameters (about 70 s)

| # | On screen | Caption | min s |
|---|---|---|---|
| 7 | Camera moves to Deck A; the rest dims | "Every deck works the same way. Most of the time a deck plays a generator, a shader that draws video live." | 7.6 |
| 8 | A row of generator names flicks by inside the box; an "ISF" tag | "Dozens come with the app, and you can add your own: they use ISF, an open shader format." | 6.3 |
| 9 | An "External video: Spout / Syphon / PipeWire" chip next to the deck | "A deck can also show live video from another app, over Spout, Syphon or PipeWire." | 5.9 |
| 10 | Deck A loads **Wave Ripples**; three parameter boxes wire into it (live value bars); Sources ramps 2 → 6 | "Each generator has its own parameters. Wave Ripples has Sources, Wavelength and Wave Speed." | 6.6 |
| 11 | **Metaballs**; boxes swap; Merge ramps | "Metaballs has Balls, Ball Size and Merge." | 3.0 |
| 12 | **Tunnel Scenes**; boxes swap; Sides steps | "Tunnel Scenes has Scene, Sides and Twist." | 3.0 |
| 13 | Control fan: knob, audio, LFO, sequencer, hardware icons wire into one parameter; an LFO is bound and its bar and the thumbnail move | "Any parameter can be turned with an on-screen knob, or driven by audio, an LFO, a sequencer or a hardware controller." | 8.4 |
| 14 | Camera to A's FX1: **Kaleidoscope** with Segments, Rotation, Zoom; the control fan flashes | "Effects work the same way. Kaleidoscope has Segments, Rotation and Zoom." | 5.1 |
| 15 | FX2 lights: **Pixelate** with Pixel Size, Color Steps | "The next slot has its own effect, with its own parameters." | 4.1 |
| 16 | `fx_swap` of slots 1 and 2; Deck A's thumbnail changes | "Order matters: Pixelate before Kaleidoscope looks different from Pixelate after it." | 5.9 |
| 17 | Camera to Trans: **Vortex Swirl** with Rotations, Radius; crossfade sweeps | "Transitions have parameters too. Vortex Swirl has Rotations and Radius." | 5.1 |
| 18 | **Film Burn**; boxes swap; control fan flashes | "Film Burn has its own, controlled the same way." | 3.4 |
| 19 | Camera to the Master chain, which lights up | "And the master chain works just like a deck's." | 3.3 |
| 20 | Reveal: the diagram shrinks into the corner mini-map while the backdrop fades and the real UI appears | "Now let's see where all of this lives in the app." | 3.5 |

The end of the intro restores the state Ch. 1 expects (Ch. 1 sources and chains, crossfade at A, BG level 0, no
LFO left bound), then Ch. 1 starts. Ch. 1's deck-purpose captions can then shrink, since the intro covered them
(follow-up, after review).

### Example picks and why

Chosen so each parameter does something you can name while it moves. No raymarchers, so drafts render fast and the
thumbnails stay smooth.

- Generators: **Wave Ripples** (Sources: you can count them), **Metaballs** (Balls, Ball Size, Merge), **Tunnel Scenes** (Scene, Sides, Twist).
- Effects: **Kaleidoscope** (Segments, Rotation, Zoom), **Pixelate** (Pixel Size, Color Steps). Swapping them is the
  order demo: pixels then mirror gives a radial pixel grid, mirror then pixels gives square pixels.
- Transitions: **Vortex Swirl** (Rotations, Radius), **Film Burn** (Burn Spread, Glow Intensity). Their ISF inputs
  have no LABEL (`rotations`, `burnSpread`); the boxes must show the same text the app shows for them.
- Master: none loaded; the beat only lights the chain, so Ch. 1's state is not disturbed.

Check in the draft: each pick is visible at thumbnail size, and the ramps (Sources 2 → 6, Merge, Sides) read clearly.

## The diagram overlay (new: `tour/PipelineDiagram.kt`)

Drawn by `TourRunner.drawOverlay()` on the foreground draw list, under the cursor and the caption. All animation runs on
the tour's simulated clock, so frames stay deterministic.

**Layout** (1920x1080 design space; the UI is always laid out at that size):

```
 [A  ]→[fx1][fx2][fx3]─┐
                       ├→[Trans]→[Behind]→[M fx1][M fx2][M fx3]→[Output]
 [B  ]→[fx1][fx2][fx3]─┘            ↑
 [BG ]→[fx1][fx2][fx3]──────────────┘
 ───────────────────────────────────────────────── only you see below
 [PV ]→[fx1][fx2][fx3]→(your monitor)
```

Deck and Output boxes 192x108 (16:9) with live thumbnails; FX slots 96x54 with the slot's effect name ("empty" when
empty) read live from the chain; Trans and Behind boxes show live thumbnails too.

**Live thumbnails**, all textures that already exist (`ImGui` draw-list `addImage`, V flipped as `MixerPanel` does):
deck source = `deck.cleanFBO.texture`, deck output = `deck.getOutputTexture()`, Trans = `mixer.blendFBO.texture`,
Behind = `mixer.masterCompositeFBO.texture`, Output = `mixer.masterFBO.texture`. Deck boxes draw over a checkerboard
so transparency (what BG fills) is visible. Per-slot FX thumbnails would need a tour-only copy of each slot's result in
`renderFxChainPass`; not planned, the deck output is enough for the order demo.

**Camera**: one 2D transform (scale + offset) applied to every coordinate and font size, eased between targets.
`pipe_focus` aims it at a node and dims the rest to about 25 %. Text stays sharp because `addText` takes a font size.

**Parameter boxes**: up to four, fanned out to the right of the focused node with wires into it, each showing the
parameter's label and a live value bar read from the real `ModulatableParameter` (so ramps, LFOs and swaps show for
free). Labels come from the ISF header, the same text the app's knobs show.

**Control fan**: five small icons (knob, audio, LFO, sequencer, hardware) with wires into one parameter box; it can
flash later without being redrawn from scratch.

**Colours and type**: `TangoPalette` only, theme-aware; the caption font.

## New script actions

| Action | Fields |
|---|---|
| `pipe` | `target` = `show` (backdrop + empty diagram), `hide`, `reveal` (shrink to the mini-map while the backdrop fades); `durationSec` |
| `pipe_add` | `target` = comma list of groups: `decks`, `fx`, `trans`, `bg`, `master`, `pv`; `durationSec` (fade and slide in) |
| `pipe_focus` | `target` = node id or `all`; `durationSec` |
| `pipe_params` | `target` = node id, `params` = list of parameter names (new `TourStep` field), empty list clears |
| `pipe_controls` | `target` = parameter name the fan wires into, `value` 1 show, 0 hide, 2 flash |
| `pipe_tag` | `target` = node id, `title` = chip text (ISF, External video, only you see this), `value` 0 removes |
| `pipe_minimap` | `target` = comma list of node ids to light, `durationSec` (shown that long, then fades) |
| `ramp_param` | as `set_param` plus `durationSec`: eases from the current value (`fx_param` gets `durationSec` too) |
| `deck_level` | `deck`, `value`: sets a deck's level, for restoring BG to 0 behind the backdrop |

Node ids: `A`, `B`, `BG`, `PV`, `A.fx1` .. `PV.fx3`, `trans`, `behind`, `master`, `master.fx1` .. `master.fx3`, `out`.
Unknown ids print `TOUR_WARN`, as for widget keys.

## The mini-map in the rest of the tour

About 320x150 in a corner, shown for 2-3 s at every chapter start with the relevant part lit, then faded, so it never sits
on top of what a caption points at. Corner to be picked from a draft frame of each page (the Perform rows fill the left,
the output and deck previews the right). It goes into the deep dives once the intro has been reviewed.

## Steps

1. `PipelineDiagram.kt`: layout, `pipe` / `pipe_add` / `pipe_tag`, live thumbnails, checkerboard. A test script under
   `scripts/` renders stills (`--tour-dump`) to tune the layout.
2. Camera and `pipe_focus`; `pipe_params` with labels and live bars; `ramp_param`, `fx_param` duration, `deck_level`.
3. `pipe_controls`.
4. `pipe reveal` and `pipe_minimap`.
5. The `pipeline` chapter in `tours/overview.json`; state restore before Ch. 1; draft render;
   `tour_determinism_check.sh`; `website/data/tour.json` if the chapter needs a summary.
6. After review: trim Ch. 1's deck captions; mini-map in the other chapters and deep dives.

Docs with each step: the action table and node ids in `docs/developer/tour_renderer.md`, both release-notes files
(tour-render support is listed there already), and the feedback log `.planning/walkthrough-tour-feedback.md`.

## Decisions (2026-10-19)

1. Example picks confirmed.
2. Length is not a constraint: the intro takes as long as it needs.
3. Mini-map at **every** chapter start, in every video: viewers may not watch in order.
4. The External video beat stays, as a chip only.
