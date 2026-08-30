# CV Modulation & Preset Grid

The Control Voltage (CV) modulation matrix is the nerve center of Liquid LSD. It allows real-time audio envelopes, transient triggers, beat clocks, LFOs, and external MIDI signals to modulate any visual parameter.

---

## The Preset Grid Matrix

The Preset Grid is located in the left panel of Performance Mode.

```
┌─────────────────┬──────────┬──────────┬──────────┬──────────┬──────────┐
│ Parameter       │ FINAL    │ MIDI     │ GEN 1    │ AUDIO    │ TRIGGER  │
├─────────────────┼──────────┼──────────┼──────────┼──────────┼──────────┤
│ Deck A / Lobes  │  ● 4.0   │  [--]    │  ( 🔘 )   │  ( 🔘 )   │  [  ]    │
│ Deck A / Zoom   │  ● 1.0   │  [--]    │  [  ]    │  ( 🔘 )   │  ( 🔘 )   │
└─────────────────┴──────────┴──────────┴──────────┴──────────┴──────────┘
```

- **Top Navigation Tabs**: Switch view focus between **Deck A**, **Deck B**, **Deck BG**, and **Deck PV**.
- **Undo / Redo History**: Maintains a 30-level undo/redo stack (`Ctrl+Z` / `Ctrl+Y`) tracking all modulator edits, additions, and parameter changes.
- **Rows**: Modulatable parameters grouped logically: Mixer, Deck Geometry, View, Color, Background, and Feedback.
- **Columns**: Active modulation sources:
  - **FINAL**: Base parameter controls, live evaluated output knobs, and parameter default resets.
  - **MIDI**: Direct MIDI CC hardware assignments.
  - **GEN 1 & GEN 2**: Configurable LFO & clock generators.
  - **AUDIO**: Audio frequency-band envelope extractors (`AMP`, `BASS`, `MID`, `HIGH`).
  - **TRIGGER**: Musical transient impulse detectors (`ONSET`, `ACCENT`).
- **Grid Cells**: Intersection points linking a source to a parameter. Active cells display a animated colored dot indicating live CV signal output.

- **Grid Knob Cell Scale & Resolution Scaling**: Grid cells and circular readout knobs automatically scale with global UI font size (`baseSize`). You can fine-tune relative knob dimensions (0.70x to 2.00x) under **Settings -> Preset Grid** via the **Grid Knob Cell Scale** slider.

---

## Generator Sources & LFO 2 Modulation

Selecting a **GEN 1** or **GEN 2** cell enables full dual-oscillator LFO shaping in the **Cell Config Panel**:

### Primary Oscillator (LFO 1 / Carrier)
- **Waveforms**: `SINE`, `TRIANGLE` (asymmetric ramp), `SQUARE` (pulse-width adjustable), and `RANDOM` (Sample & Hold).
- **Clock Mode**:
  - **`TIME`**: Measured in seconds (`Fast` / `Medium` / `Slow` sliders).
  - **`BEAT`**: Synced to interpolated beat clock subdivisions (`1/8`, `1/4`, `1/2`, `1`, `2`, `4`, `8` beats).
- **Waveshaping**:
  - **Slope**: Adjusts asymmetry (`0.5` = symmetric triangle, `1.0` = slow rise/sharp drop).
  - **Morph**: Log-cosh waveshaping (`0.0` = sharp triangle, `1.0` = smooth sine).
  - **Hold**: Compresses transition region to create peak plateaus.

### Secondary Modulator (LFO 2)
LFO 2 is a second internal oscillator that modulates LFO 1:
- **`AM`** (Amplitude Modulation): LFO 2 scales LFO 1's depth (`carrier * (1 + lfo2 * depth)`).
- **`PM`** (Phase Modulation): LFO 2 dynamically shifts LFO 1's phase offset.
- **`ADD`** (Additive): Combines LFO 2 directly with LFO 1.

---

## Audio Envelope Followers & Dynamics

When configuring an **AUDIO** modulation cell (`audio_amp`, `audio_bass`, `audio_mid`, `audio_high`), each frequency band has its own independent dynamics follower:

- **Follower Preset Dropdown**:
  - **`Raw (Instant Jitter)`**: Bypasses the envelope follower. Modulation tracks instantaneous block RMS directly for maximum high-frequency visual flutter.
  - **`Punchy (Fast)`**: $5\text{ ms}$ attack, $150\text{ ms}$ decay. Captures drum transients instantly and drops cleanly between beats.
  - **`Smooth Swell`**: $40\text{ ms}$ attack, $400\text{ ms}$ decay. Turns sharp beats into smooth, breathing pulses.
  - **`Slow Pulse`**: $100\text{ ms}$ attack, $800\text{ ms}$ decay. Gradual swell with a lingering release tail.
  - **`Ambient Drift`**: $250\text{ ms}$ attack, $1500\text{ ms}$ decay. Slow energy swells ideal for ambient drifting.
  - **`Custom`**: Exposes fine-grained **Attack** ($0\text{ ms} \dots 500\text{ ms}$) and **Decay** ($10\text{ ms} \dots 3000\text{ ms}$) sliders. Selecting `Custom` automatically inherits the exact attack/decay timings from the previously active preset.
- **Dual-Trace Oscilloscope**: The oscilloscope plots the raw audio energy in a faint ghost trace ($35\%$ opacity) beneath the solid smoothed follower curve, allowing you to visually see how the Attack catches transients and how the Decay tail descends.

---

## Modulator Attributes & Operator Math

Modulators dictate how CV signals modify parameter base values:

$$\text{Evaluated Value} = \text{baseValue} + \text{Modulation Effect}$$

### Modulation Operators
- **`ADD`**: CV signal is scaled to the parameter's range and added to `baseValue`.
- **`MUL`**: Multiplicative ring-mod style ($result = baseValue \times (1.0 + \text{CV} \times \text{depth})$).
- **`SCALE`**: Attenuates base value ($result = baseValue \times (1.0 - \text{depth} + \text{CV} \times \text{depth})$).

All final evaluated values are automatically clamped to parameter hardware bounds (`minClamp` to `maxClamp`).

---

## Power-User Mouse & Keyboard Shortcuts

Liquid LSD includes mouse and keyboard power shortcuts designed for live performance speed:

### Parameter Slider Scrubbing
- **Hover Scroll-Wheel**: Hover over any slider or numeric box and scroll mouse wheel to adjust values without clicking:
  - *Unmodified*: `±0.001` fine step.
  - *`Shift` + Scroll*: `±0.01` medium step.
  - *`Ctrl` + `Shift` + Scroll*: `±0.1` coarse step.
- **Middle-Click Reset**: Middle-click any slider track or `FINAL` knob cell to instantly reset parameter to factory default.

### Preset Grid Shortcuts
- **Middle-Click Cell Mute**: Middle-click any active/muted grid cell to toggle its `Muted` (preview) state on/off immediately. Muted cells display **35% arc opacity with a sans-serif 'M'** centered in the knob. While muted, modulation is blocked from `Final`, but the Oscilloscope in `CellConfig` stays 100% live for real-time waveform previewing. Middle-clicking an unmapped CV cell automatically assigns a default modulator.
- **Middle-Click Parameter Reset**: Middle-click the row label or `FINAL` cell to reset the parameter to default.
- **Copy / Paste (`Ctrl+C` / `Ctrl+V` or `Cmd+C` / `Cmd+V`)**:
  - Selecting a CV cell (LFO, Audio, Trigger, MIDI) and pressing `Ctrl+C` copies that cell's active modulators. Pasting onto another cell (`Ctrl+V`) routes the modulator to the new source type with automatic envelope and frequency mapping.
  - Selecting the row label or `FINAL` cell and pressing `Ctrl+C` copies the full parameter row (base values, bounds, randomization flags, and all modulators). Pasting onto another parameter (`Ctrl+V`) scales depths and DC offsets to the destination clamp range.
- **Clear / Reset (`Delete` or `Backspace`)**: Clears the modulators on the selected CV cell, or resets the parameter to default if the `FINAL` cell or row is selected.
- **Master Oscilloscope Mute Toggle**: Click the `[ LIVE ]` / `[ MUTED ]` toggle button in the top-right corner of the Cell Config Oscilloscope header to toggle cell mute status.
- **Right-Click Row Context Menu**:
  - Copy / Paste parameter settings.
  - Reset parameter to default.
  - **Mute / Unmute Modulator(s)**: Toggle cell mute state.
  - **📝 Add/Edit Parameter Note…**: Opens `NoteEditorModal` to attach a custom user note to this parameter.
- **`Ctrl+Z` / `Ctrl+Y`**: Global Undo / Redo across all modulation edits (up to 30 steps).
- **Settings Summary**: View **Settings -> Keyboard Shortcuts** for a complete, grouped list of all shortcuts across the application.
