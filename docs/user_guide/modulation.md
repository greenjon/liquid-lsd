# Modulation

Modulation is how you make your visuals react to music, touch, MIDI, oscillators, or a sequencer. Instead of just setting parameters to fixed values, you connect them to live signals — audio energy, LFOs, step patterns, or MIDI knobs — and let those signals drive what's happening on screen.

---

## The CV Grid

The CV grid is the middle column of **Edit** (click a row's gear in the Perform view). It's a matrix:

- **Rows** = visual parameters (Lobes, Zoom, Hue, Feedback Decay, etc.)
- **Columns** = modulation sources (manual value, MIDI, LFO, Sequencer, Audio)

Click any cell at the intersection of a parameter and a source type to configure that connection. Active cells show an animated readout knob so you can see the live signal at a glance.

The side rail on the left (`[MST] [OUT] [A] [B] [BG] [PV]`) switches between the master levels and clock controls, the output stage and the decks; the section tabs above the grid switch between a deck's source (`SRC`) and its `FX` chain, the output stage's `FX` / `TRANS`, or the MST tab's `LEVELS` / `CTRL`. The selected cell's details appear in the Modulation column on the right.

### Columns

Each column is colour-coded:

- **VAL** (mint cyan) — The manual base value, plus range limits and a reset button.
- **MIDI** (violet) — Hardware MIDI controller assignments.
- **LFO** (sky blue) — Low-frequency oscillators.
- **SEQ** (lime green) — Step sequencer.
- **AUD** (amber) — Audio modulation.

You can hide columns you're not using via the **⋮** kebab menu, positioned just left of the **VAL** column header and aligned with the row action buttons below it. That menu also shows warnings if a loaded preset uses modulators that are currently hidden or offline, with a one-click **Turn On Needed Columns** button to sort it out.

---

## Audio Modulation

Clicking an **AUD** cell opens the audio modulator config. Each parameter supports two independent audio connections (Audio 1 and Audio 2).

**Auto Gain:** audio signals are normalized against each band's own recent peak (Preferences > Audio > Auto Gain, on by default), so a modulator behaves the same whether the feed is quiet or hot. Switch it off for a fixed scale.

### Frequency bands

| Band | What it tracks                                     |
| ---- | -------------------------------------------------- |
| AMP  | Full-spectrum energy                               |
| BASS | Low end — kick drums, sub-bass (below ~150 Hz)     |
| MID  | Midrange — vocals, snares, lead synths (~1 kHz)    |
| HIGH | Top end — hi-hats, cymbals, texture (above ~5 kHz) |

### Detection modes

- **Continuous** — Tracks the ongoing energy level of the band. Good for sustained movement, breathing dynamics, amplitude-following effects.
- **Transient** — Fires on attacks and hits. Detects the moment energy *increases* in the band, making it sharp and percussive. Great for triggering flashes, jumps, or snaps on drum hits.

### Response profiles

These control how quickly the signal rises and falls:

| Profile              | Character                                                        |
| -------------------- | ---------------------------------------------------------------- |
| Instant (Raw Jitter) | No smoothing — raw and jittery.                                  |
| Snap                 | Immediate attack, fast release. Good for fast percussive tracks. |
| Punchy               | Slight attack, medium release. Captures rhythm cleanly.          |
| Smooth Swell         | Slower attack, long release. Peaks become gentle pulses.         |
| Slow Pulse           | Gradual rise, lingering tail.                                    |
| Ambient Drift        | Long, fluid swells. Ideal for ambient or drone music.            |
| Custom               | Set your own attack and decay times.                             |

Selecting **Custom** inherits the timings from whatever profile was active before, so you can fine-tune from a sensible starting point.

### The oscilloscope

The oscilloscope shows two traces: the raw audio energy (faint ghost trace) and the smoothed follower output (solid line). This makes it easy to see how the profile is shaping the signal before it reaches your parameter.

### Range (Min / Max)

The dual-handled range slider at the bottom sets the floor and ceiling of the modulation output. For example, if you want bass energy to push Lobes between 3 and 8, set Min to 3 and Max to 8.

Enabling randomization adds independent drift sliders for the Min and Max bounds, so the range itself wanders gently over time.

---

## LFO Modulation

Clicking an **LFO** cell opens the LFO config. Each cell has a primary oscillator and an optional secondary modulator.

### Primary LFO (LFO 1)

**Waveforms:**

- **Sine** — Smooth, rounded oscillation.
- **Triangle** — Linear rise and fall, adjustable asymmetry.
- **Square** — Snaps between two values. Adjust pulse width with the Hold control.
- **Random (Sample & Hold)** — Holds a random value for each cycle, then jumps to a new one.

**Clock mode:**

- **Time** — Set the period in seconds. Use Fast / Medium / Slow sliders. Changing the period (by hand, from a controller, a macro or a preset morph) only changes how fast the LFO runs; it never jumps.
- **Beat** — Synced to the beat clock. Subdivisions from 1/8 beat to 8 bars.

**Waveshaping:**

- **Slope** — Makes the waveform asymmetric. At 0.5 it's symmetric; at 1.0 you get a slow rise and a fast drop.
- **Morph** — Softens sharp corners. At 0.0 you get a pure triangle; at 1.0 you get a smooth sine-like curve.
- **Hold** — Compresses the transition into a shorter moment and adds a plateau at the peak.

**Range (Min / Max):** Sets the output range directly. No need to think in terms of depth and offset — just say "I want this to move between 2.0 and 6.0" and set those values.

### Secondary Modulator (LFO 2)

LFO 2 modulates LFO 1 to add movement and complexity:

- **AM (Amplitude Modulation)** — LFO 2 scales LFO 1's depth up and down, making the oscillation breathe.
- **PM (Phase Modulation)** — LFO 2 shifts LFO 1's phase, making it wobble in time.
- **Add** — LFO 2's output is added directly to LFO 1's output.

---

## Step Sequencer

The sequencer outputs a stepped voltage pattern that advances with each beat or at a set interval — good for geometric shifts, colour steps, and rhythmic stutter effects.

> **Note:** The sequencer is on by default. If you don't use it, turn it off in **Preferences → General** (under Features, "Enable Step Sequencer"), or from the **⋮** kebab menu left of Edit's **VAL** column header, to hide its column and tab.

### The step editor

Each step is a bar. Bars sit in rows of up to 16, so a 32-step pattern is two rows. The step playing now is outlined, and every fourth step is numbered. A white line traces the output as it will actually move: flat steps when Step Hold is 100%, slides when it is lower. A dot rides the line at the current position.

- **Paint** — click and drag across the bars to draw a pattern. Values snap to the **Snap** grid (5% by default; pick Off, 10%, 12.5% or 25% in the toolbar), and holding `Shift` paints freely. A fast drag fills the steps in between.
- **Nudge** — hover a bar and scroll, by one Snap step per notch (`Shift` for 1%, `Ctrl` for 20%).
- **Keyboard** — clicking a bar selects it (thin outline). While the pointer is over the bars, `Left` / `Right` move the selection, `Up` / `Down` nudge it (`Shift` for 1%), `Delete` or `Backspace` resets it, and `Enter` opens the number box.
- **Type a value** — double-click a bar, type a number (0 to 100, or -100 to 100 for a bipolar parameter) and press Enter.
- **Reset** — right-click or middle-click a bar to set it to 0.

**Length** is a 1 to 32 slider, so odd lengths such as 5 or 7 work. Steps past the end are kept and shown ghosted after the last playing step, so lengthening again brings them back. **Phase** starts the pattern part-way through, and **Sync to Step 1** restarts it now.

### Pattern tools

The row above the bars edits the active steps:

- **Randomize** — random values. **Auto** (when randomization is enabled) — also re-roll the pattern whenever a preset or deck is randomized; the deck morph then slides between patterns. **Invert** — flip every step.
- **Shift <** / **Shift >** — rotate the pattern one step earlier or later.
- **Copy** / **Paste** — copy a pattern (with its length) from one sequencer to another.
- **Fill...** — Ramp Up, Ramp Down, Triangle, Alternate, Pulse or All On.
- **Euclid...** — spread N hits evenly across the pattern, the classic euclidean rhythm, with a Rotate control and a preview before you apply.
- **Clear** — reset all 32 steps to 0.
- **Snap** — the grid painting and nudging snap to.

### Timing

- **Beat** — Syncs to the beat clock. Pick a subdivision from 1/16 to 4 beats per step.
- **Time** — Wall clock seconds per step (0.01s to 5.0s).
- **Frame** — Advances every N render frames (1 to 120).

Changing the clock unit keeps the same step duration where it can. Changing a Time or Frame interval while it plays keeps the current step and only changes the speed.

### Glide

- **Step Hold** slider — At 100% the value jumps instantly and holds flat for the full step. At 0% it glides smoothly from one step to the next over the whole duration. In between you get a hold then glide. It has a dice to randomize within a range, like the other sliders.
- **Glide Curve** — Linear is a straight ramp; Smooth is an S-curve with gentle acceleration and deceleration. Greyed out while Step Hold is 100%, since nothing glides.

### Depth and Offset

**Depth** scales the pattern and **Offset** shifts it up or down before it is applied to the parameter. Both can be randomized.

---

## Modulation Operators

When you set up a modulator, you also choose how it combines with the base value:

- **ADD** — The modulation signal is added on top of the base value. Most common.
- **MUL** — The signal multiplies the base value. Quiet when the base is low, loud when it's high. Like ring modulation.
- **SCALE** — Attenuates the base value based on the signal. At zero signal you get full base value; at full signal the base is scaled down. Useful for "duck" effects.

All outputs are clamped to the parameter's valid range automatically.

---

## Shortcuts & Power-User Tips

### How values are shown

Parameters, LFO and sequencer fields, and macro knobs whose range is 0 to 1 (or -1 to 1) display as **0 to 100** (or **-100 to 100**), with a decimal only when the value needs one (`37`, `37.4`). Type either form. Angles stay in degrees, and parameters with their own units (counts, Hz, seconds, BPM) keep them. This is display only: presets, MIDI and OSC still use the underlying 0 to 1 values.

### Stepped parameters

Some parameters are really a list of choices (a mode, a palette, a segment count). Their knobs show a **tick mark for each choice** (none when there are more than 16), and the value always lands exactly on a choice: a knob, LFO, audio source, macro, MIDI or OSC sweep passes through the choices instead of between them. In an FX row's focus mode the knob shows the choice's name when it has one (for example a 3D mode), otherwise its number. Hover a stepped value for the number of choices. In the **VAL** panel a stepped parameter with up to 16 choices gets a drop-down list of them (for example the 3D mode), and its live and starting values read as the choice name.

### Adjusting values without clicking

Hover over any slider or number and scroll the mouse wheel:

- Scroll — fine step
- `Shift` + scroll — medium step
- `Ctrl+Shift` + scroll — coarse step

Middle-click any slider to reset to factory default.

### Edit shortcuts

These act on the selected cell in Edit (see [Macros & the Rack](macros_and_rack.md) for which
Edit receives them when several are open).

| Action                      | Shortcut                                |
| --------------------------- | --------------------------------------- |
| Save active preset          | `Ctrl+S`                                |
| Save preset as...           | `Shift+Ctrl+S`                          |
| Undo                        | `Ctrl+Z`                                |
| Copy cell                   | `Ctrl+C` (with a CV cell selected)      |
| Paste cell                  | `Ctrl+V` (onto another cell)            |
| Copy full parameter row     | `Ctrl+C` (with the VALUE cell selected) |
| Clear / reset selected cell | `Delete` or `Backspace`                 |
| Toggle cell mute            | Middle-click the modulation cell        |
| Toggle row mute (all modulators) | Right-click or middle-click the VAL cell |

**Muted cells** still show the live oscilloscope but don't affect the parameter — useful for previewing what a connection would do without committing to it.

**Toggle all modulators:** Right-clicking or middle-clicking the **VAL** cell toggles mute for all active modulators assigned to that parameter. If the parameter has no modulators, middle-clicking the VAL cell resets the base value to default.

**Copying cells:** Copy an LFO, audio, or MIDI cell and paste it onto any compatible cell — the modulator follows automatically. Copy a full parameter row (from the VALUE cell) and paste it onto another parameter to clone all settings, rescaled to the new parameter's range.

**Right-click** any parameter row label or kebab button for a context menu with randomize, copy, paste, reset, mute/unmute all modulators, and note-editing options.

See **Preferences → Keyboard Shortcuts** for a full grouped list of all shortcuts in the app.
