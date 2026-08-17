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

- **Top Navigation Tabs**: Switch view focus between **Deck A**, **Deck B**, and **Deck C**.
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
- **Middle-Click Cell Bypass**: Middle-click any grid cell to toggle its `bypassed` state on/off immediately.
- **Right-Click Row Context Menu**:
  - Copy / Paste parameter settings.
  - Reset parameter to default.
  - **📝 Add/Edit Parameter Note…**: Opens `NoteEditorModal` to attach a custom user note to this parameter.
- **`Ctrl+Z` / `Ctrl+Y`**: Global Undo / Redo across all modulation edits (up to 30 steps).
