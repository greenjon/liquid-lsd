# CV Modulation

The heart of Liquid LSD is its Control Voltage (CV) modulation matrix. This system allows you to route audio characteristics or generator signals to modulate any rendering parameter.

## The Patch Grid

The Patch Grid is a visual modulation matrix displayed in the left panel of the application.

- **Rows**: The parameters available for modulation (grouped hierarchically: Mixer, Deck A, Deck B, along with subcategories like Geometry, Color, Background, and Feedback).
- **Columns**: The active modulation sources. The grid columns are:
  - **FINAL**: A special display column to set parameter base values and monitor real-time outputs.
  - **MIDI**: Allows learning and mapping external hardware MIDI controllers.
  - **GEN 1 & GEN 2**: Configurable generators (e.g. LFO, clock phase, random).
  - **AUDIO**: Audio frequency-band envelope analysis.
  - **TRIGGER**: Real-time musical transient detection.
- **Grid Cells**: The intersection points where you can create a modulator linking a source to a parameter.
  - Active cells display a faint circle containing an animated dot indicating the current real-time value of the CV signal.
  - Bypassed cells are rendered in muted grey.

---

## CV Sources

CV sources generate values that modulate rendering parameters. When you select a cell, the **Cell Config Panel** lets you map specific internal signals:

### Audio-Derived Sources (Extracted from JACK Input)
Under the **AUDIO** column, you can select which band-split envelope to use:
- **`audio_amp`**: Overall root-mean-square (RMS) amplitude of the incoming audio signal.
- **`audio_bass`**: RMS amplitude in the low-frequency band.
- **`audio_mid`**: RMS amplitude in the mid-frequency band.
- **`audio_high`**: RMS amplitude in the high-frequency band.

Under the **TRIGGER** column, you can select transient triggers:
- **`trigger_onset`**: A brief impulse trigger generated on detected audio transients.
- **`trigger_accent`**: A transient trigger generated on strong musical accents.

*(Note: The current estimated tempo is also available under the `bpm` source).*

### Generator Sources (Calculated in Real-Time)
Under **GEN 1** and **GEN 2**, you can configure unified generators:
- **Waveforms**: Sine, Triangle, Square, or Random (Sample & Hold) waveforms.
- **Timing modes**: Time-based (free-running LFO at Fast/Medium/Slow speeds) or Beat-synced (subdivisions like 1/8, 1/4, 1/2, 1, 2, 4, 8, etc.).
- **Controls**: Phase Offset and Slope (glide/smoothing amount).

---

## Modulators & Operator Math

When you select a grid cell, the **Cell Config Panel** (middle panel) opens. Here, you can configure how the CV source affects the target parameter.

### Modulator Attributes
- **Bypass/Active Toggle**: Instantly enable or disable this specific modulation routing.
- **Amplitude (Amplitude Slider)**: Controls the strength/depth of the modulation.
- **DC Offset**: Shifts the center offset of the incoming CV value.
- **Operator**: Defines how the modulation value is combined with the parameter's base value:
  - **ADD**: The modulation value is added directly:
    $$result = baseValue + (cv * amplitude + dcOffset)$$
  - **MUL**: The parameter's base value is scaled:
    $$result = baseValue * (1.0 + (cv * amplitude + dcOffset))$$
  - **SCALE**: The parameter's base value is scaled relative to the amplitude:
    $$result = baseValue * (1.0 - amplitude + (cv * amplitude + dcOffset))$$

*The final evaluated parameter value is always clamped to its defined limits (usually `0.0` to `1.0`).*

---

## Oscilloscopes & Monitors

Monitoring real-time values is essential to dialing in responsive patches.

- **Cell-Specific Oscilloscope**: Inside the Cell Config panel, a scrolling waveform display shows the history of the selected CV source and how it affects the parameter's evaluated output.
- **Sound Analysis Panel**: (Plan under active development) A dedicated window displaying the raw audio waveform, frequency band splits, and calculated BPM estimate.
