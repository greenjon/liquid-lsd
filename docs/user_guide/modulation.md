# Modulation

Modulation is how you make your visuals react to music. Instead of just setting parameters to fixed values, you connect them to live signals — audio energy, LFOs, step patterns, or MIDI knobs — and let those signals drive what's happening on screen.

---

## The CV Grid

The **Preset Grid** is the left panel, visible whenever the Library is in Half Height or Docked mode. It's a matrix:

- **Rows** = visual parameters (Lobes, Zoom, Hue, Feedback Decay, etc.)
- **Columns** = modulation sources (manual value, MIDI, LFO, Sequencer, Audio)

Click any cell at the intersection of a parameter and a source type to configure that connection. Active cells show an animated readout knob so you can see the live signal at a glance.

The tabs at the top switch between decks (A, B, BG, PV) and the master mixer.

### Columns

Each column is colour-coded:

- **VAL** (mint cyan) — The manual base value, plus range limits and a reset button.
- **MIDI** (violet) — Hardware MIDI controller assignments.
- **LFO** (sky blue) — Low-frequency oscillators.
- **SEQ** (lime green) — Step sequencer.
- **AUD** (amber) — Audio modulation.

You can hide columns you're not using via the **⋮** kebab menu to the right of the column headers. That menu also shows warnings if a loaded preset uses modulators that are currently hidden or offline, with a one-click **Turn On Needed Columns** button to sort it out.

---

## Audio Modulation

Clicking an **AUD** cell opens the audio modulator config. Each parameter supports two independent audio connections (Audio 1 and Audio 2).

### Frequency bands

| Band | What it tracks |
|------|---------------|
| AMP | Full-spectrum energy |
| BASS | Low end — kick drums, sub-bass (below ~150 Hz) |
| MID | Midrange — vocals, snares, lead synths (~1 kHz) |
| HIGH | Top end — hi-hats, cymbals, texture (above ~5 kHz) |

### Detection modes

- **Continuous** — Tracks the ongoing energy level of the band. Good for sustained movement, breathing dynamics, amplitude-following effects.
- **Transient** — Fires on attacks and hits. Detects the moment energy *increases* in the band, making it sharp and percussive. Great for triggering flashes, jumps, or snaps on drum hits.

### Response profiles

These control how quickly the signal rises and falls:

| Profile | Character |
|---------|-----------|
| Instant (Raw Jitter) | No smoothing — raw and jittery. |
| Snap | Immediate attack, fast release. Good for fast percussive tracks. |
| Punchy | Slight attack, medium release. Captures rhythm cleanly. |
| Smooth Swell | Slower attack, long release. Peaks become gentle pulses. |
| Slow Pulse | Gradual rise, lingering tail. |
| Ambient Drift | Long, fluid swells. Ideal for ambient or drone music. |
| Custom | Set your own attack and decay times. |

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
- **Time** — Set the period in seconds. Use Fast / Medium / Slow sliders.
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

> **Note:** The sequencer is disabled by default. Enable it in **Settings → General** or **Settings → Preset Grid**.

### The step grid

Steps are laid out in rows of 8: 8 steps in one row, 16 in two, 32 in four. Click any cell to type a value (0.0 to 1.0), or hover and scroll to adjust. The active step is highlighted in bright green.

**Keyboard editing shortcuts** (when a step is focused):
- `Up` / `Down` — fine step (±0.001)
- `Shift` + `Up` / `Down` — medium step (±0.01)
- `Ctrl+Shift` + `Up` / `Down` — coarse step (±0.1)
- Middle-click — reset to 0.0
- `Tab` / `Shift+Tab` — move between steps

### Timing

- **Beat** — Syncs to the beat clock. Pick a subdivision from 1/16 to 4 beats per step.
- **Time** — Wall clock seconds per step (0.01s to 5.0s).
- **Frame** — Advances every N render frames (1 to 120).

### Glide

- **Step Hold** slider — At 100% the value jumps instantly and holds flat for the full step. At 0% it glides smoothly from one step to the next over the whole duration. In between you get a hold then glide.
- **Glide Curve** — Linear is a straight ramp; Smooth is an S-curve with gentle acceleration and deceleration.

---

## Modulation Operators

When you set up a modulator, you also choose how it combines with the base value:

- **ADD** — The modulation signal is added on top of the base value. Most common.
- **MUL** — The signal multiplies the base value. Quiet when the base is low, loud when it's high. Like ring modulation.
- **SCALE** — Attenuates the base value based on the signal. At zero signal you get full base value; at full signal the base is scaled down. Useful for "duck" effects.

All outputs are clamped to the parameter's valid range automatically.

---

## Shortcuts & Power-User Tips

### Adjusting values without clicking

Hover over any slider or number and scroll the mouse wheel:
- Scroll — fine step
- `Shift` + scroll — medium step
- `Ctrl+Shift` + scroll — coarse step

Middle-click any slider or value cell to reset to factory default.

### Preset Grid shortcuts

| Action | Shortcut |
|--------|---------|
| Save active preset | `Ctrl+S` |
| Save preset as... | `Shift+Ctrl+S` |
| Undo / Redo | `Ctrl+Z` / `Ctrl+Y` |
| Copy cell | `Ctrl+C` (with a CV cell selected) |
| Paste cell | `Ctrl+V` (onto another cell) |
| Copy full parameter row | `Ctrl+C` (with the VALUE cell selected) |
| Clear / reset selected cell | `Delete` or `Backspace` |
| Toggle cell mute | Middle-click the cell |

**Muted cells** still show the live oscilloscope but don't affect the parameter — useful for previewing what a connection would do without committing to it.

**Copying cells:** Copy an LFO, audio, or MIDI cell and paste it onto any compatible cell — the modulator follows automatically. Copy a full parameter row (from the VALUE cell) and paste it onto another parameter to clone all settings, rescaled to the new parameter's range.

**Right-click** any parameter row for a context menu with copy, paste, reset, mute, and note-editing options.

See **Settings → Keyboard Shortcuts** for a full grouped list of all shortcuts in the app.
