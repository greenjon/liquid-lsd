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

**Auto Gain:** audio signals are always normalized against each band's own level over the last 20 seconds or so, so a modulator behaves the same whether the feed is quiet or hot; you don't set it up. It keeps the music's shape: during a breakdown it holds on to the level from before, so the breakdown looks calm for as long as it lasts and the drop hits, and when a quieter or louder song settles in it adopts that as the new normal. To make the picture follow the music more or less, use **Audio Response** (below). **Input Trim** (Preferences > Audio Hardware) is only for setting up the input: raise it if the panel says the input is too quiet for beat detection.

**Built-in responses:** most stock sources, every stock effect and every stock transition but Linear Crossfade ship with audio and beat modulators already wired on their key parameters: one running, and one or two alternates sitting bypassed in the same row, so you switch an alternate on (and the running one off) by clicking its AUD or LFO cell. They are ordinary modulators, so edit or delete them freely. To make everything that follows the music move more or less, use **Preferences > Audio Hardware > Audio Response** (0% = nothing follows the music, 100% = as tuned, up to 200%); it scales the audio signals themselves, so it applies to built-in and hand-made audio modulators alike, and leaves LFOs and the beat alone. A default you saved yourself replaces the built-in response, and a preset that stores its own parameters replaces it too. On an effect, a response on the parameter its main knob (Metaknob) drives multiplies rather than adds (MUL), so an effect turned down to zero stays off and turning it up makes the hits bigger too. The stock FX chains carry the built-in effect responses; a chain you saved yourself keeps the parameters it stored. A transition's response only plays during a fade (with the crossfader fully on A or B the visible deck is shown as is), and the stock transition presets carry the same responses. Luminous Flash's Spread and Film Burn's Burn Spread have none on purpose: pushed above about 45% they make the picture pop the moment a fade starts.

**Built-in random ranges:** every stock shader source also ships with a random range (the **Initial Range** slider with its dice on) on the parameters that change its look: types, colour modes and palettes, twists, folds, sizes, glow, hue and saturation. The deck die rolls new values inside them, and the deck's **Rand** control fades from one roll to the next. Controls that decide whether the picture is watchable at all (Brightness, Quality, reseed triggers) have none. Each range stops short of the parameter's limit by the reach of its running audio or LFO response, so a roll never leaves the response pressed against a limit. Stepped parameters (types, modes, palettes, side counts) give every step an equal chance and switch halfway through a Rand fade rather than stepping through the values in between. A parameter a macro knob drives takes part too: the roll moves the knob. **Ctrl+Z** after a roll puts the values and knob positions back. Like the audio responses, a default you saved yourself or a preset that stores its own parameters replaces the ranges.

Stock **effects** and **transitions** ship random ranges the same way. The deck die and Rand roll the effects in that deck's chain along with its source (the whole-rig die and Rand All also roll the Master FX chain). An effect's main knob, its Metaknob, is never rolled: it says how much of the effect you get, so the roll changes the effect's look (angle, segments, centre, palette, mode) and leaves the amount to you. Invert and Luma Key have nothing else to roll. The transition die on the Transitions row rolls only the transition's ranged inputs, inside their ranges, and its TRANS knobs move with it; Rand All rolls the transition too. Every transition range is kept low enough that nothing shows with the crossfader at rest. The stock FX chains carry the ranges too; a chain you saved yourself keeps what it stored.

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
- **Section** — Follows the shape of the song instead of its beats. Pick one of two signals:
  - **Energy** compares how loud the music is now with the last 20 seconds or so. It rests in the middle of the range during normal playing, sinks toward Min in a breakdown and stays there until the drop, and rises toward Max when the song gets louder. Use it to calm things down in a breakdown (smaller, slower, darker) and open them up when the music builds.
  - **Drop** jumps to Max when a breakdown ends with the bass coming back, then falls back to Min over 4 bars. Use it for one big hit on the drop: a flash, a zoom punch, an effect swept in. The drop is detected a second or two late, so the hit is dated back to the beat the bass came back on. It starts slightly decayed, but in time with the music.

  Section signals need about 20 seconds of music before they react. They are not scaled by Audio Response and have no response profile (they're already smooth), so only the range applies. A breakdown has to last at least 4 seconds before its end counts as a drop.

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

Min and Max are measured from the parameter's set value, and moving one never moves the other. Audio sources only push upward from silence, so Min is the resting offset and Max is the loudest peak. Section **Energy** is the exception: it swings both ways, so it rests halfway between Min and Max, with Min the depth of a breakdown and Max a much louder passage.

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

**Range (Min / Max):** Sets the output range directly. No need to think in terms of depth and offset — just say "I want this to move between 2.0 and 6.0" and set those values. Min and Max are the lowest and highest the LFO reaches, whatever the parameter's own range, and moving one leaves the other where it is. (The LFO swings evenly either side of the midpoint, so on a 0-to-1 parameter a Min below the set value pulls the parameter down as well as up; the result is clamped at the parameter's limits.)

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
