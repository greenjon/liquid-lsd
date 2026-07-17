# Presets & MIDI Mapping

Liquid LSD contains patch saving mechanisms and external MIDI controller integration.

## Preset & Patch Management

Patches store the complete state of the workstation, including all parameter values, feedback settings, and active CV modulations.

### Serialization Format
- Patches are saved as JSON files using the `kotlinx.serialization` library with a `.lsd` file extension.
- All patch files (deck presets and full session saves) live in `presets/patches/`.

### Copy & Paste (Base Column)
- You can copy the base parameter settings of one deck and paste them onto the other.
- Use the right-click menu or keyboard shortcuts within the Base column of the Patch Grid to copy/paste parameter structures.

---

## MIDI Controller Mapping

You can control parameter base values and grid cell modulations using external hardware controllers.

### MIDI Learn Mode
1. **Activate Learn Mode**: In the UI, click the MIDI button next to a target parameter slider or grid cell.
2. **Send CC Message**: Rotate a knob, move a slider, or press a button on your connected MIDI controller.
3. **Map Confirmation**: The system will detect the MIDI Control Change (CC) message, read the channel and CC ID, and bind it to the target automatically.

### Where MIDI Assignments are Saved
- **Base Parameters (MIDI Profiles)**: MIDI mapping configurations for parameter sliders (e.g. base values) are stored in the active MIDI profile located at `presets/midi/<profile_name>.json` (usually `default.json`). This separates your physical hardware maps from visual patches, allowing you to use different MIDI controllers without modifying visual presets.
- **Grid Cell Modulators (Visual Patches)**: When you map a MIDI CC to modulate a cell directly in the Patch Grid (the **MIDI** column), it is stored as a `CvModulator` with its `sourceId` set to the midi ID (e.g. `midi_cc_0_42`) directly inside the visual patch file.
- **Portability**: Visual patch files preserve their grid cell MIDI modulations, while base parameter controls remain mapped to your current hardware mapping profile. The active MIDI mapping profile can be selected in the UI.
