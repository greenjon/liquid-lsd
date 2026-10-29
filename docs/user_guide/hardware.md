# Hardware Control

Liquid LSD can be played from a hardware controller, a tablet or a phone. There are three ways in; pick the one that matches what you own, and mix them freely.

| You have | Use | Setup |
|---|---|---|
| A **DJ TechTools Midi Fighter Twister** | The built-in Twister profile | Plug it in. Optional one-time settings file for the Midi Fighter Utility |
| **Any other MIDI controller** (knobs, faders, pads, keyboard) | MIDI Learn | Enable MIDI, right-click a control, move a knob |
| **TouchOSC** or another OSC app on a phone or tablet | OSC Learn | Enable the OSC server, point the app at your computer |

MIDI and OSC are both **off by default**. Turn them on in **Preferences** (`Ctrl+,`) under **MIDI Controls** and **OSC Controls**. Each tab has a live monitor that shows every incoming message, which is the quickest way to check that a device is talking to the app.

## The Midi Fighter Twister

The Twister is the controller the app is built around. Its 16 encoders are the 16 knobs of the Performance Matrix: the 4×4 grid on the hardware mirrors the 4×4 grid on screen, and whatever a row is showing is what its encoders control.

- **Pages.** The hardware banks (or, in native mode, the top-right side button) step through the Perform pages: **A/B**, **BG/PV** and **MIXER**. The screen follows the hardware, and clicking a tab on screen moves the encoders to match.
- **Effects.** On an FX row the first knob is the chain's Super Knob and the other three are the slots' Metaknobs. A tap on a knob's switch bypasses a slot, and **Shift** + tap focuses it so the whole row becomes that effect's parameters.
- **Chain view.** **Shift** + tap a deck's source knob and the whole Twister becomes that deck: one row per FX slot, then its source row at the bottom, so all three effects are under your hands. The bottom-right side button turns Load on, which swaps the bottom row for loading: knob 16 scrolls the effect list, as it does in every browse mode, and knobs 13-15 load the highlighted effect into slot 1, 2 or 3.
- **Browsing.** The side buttons open the Library and the source and effect pickers, so you can load a source or effect without touching the mouse.
- **Lights.** Rings show each knob's value. Knob 13 on the MIXER page pulses on the beat, shows the tempo and taps tempo when pushed. In Pickup mode, an FX slot waiting for the Super Knob blinks amber.
- **Native mode.** With the optional XT firmware, rings change style to suit each parameter and the switch LEDs show the row colour.

Setup, every gesture, a worked example and troubleshooting are in [Controller Profiles: Midi Fighter Twister](performance_controls.md#controller-profiles-midi-fighter-twister). Jump straight to [one-time setup](performance_controls.md#twister-setup), [gestures](performance_controls.md#twister-gestures), [side buttons](performance_controls.md#twister-side-buttons) or the [XT firmware mode](performance_controls.md#twister-xt). To change what a page controls, see [Perform Pages](performance_controls.md#perform-pages).

## Other MIDI controllers

1. Connect the controller and enable **Preferences → MIDI Controls → Enable MIDI Subsystem**. New devices are picked up without restarting.
2. **Right-click** the on-screen knob, slider or button you want to control. Choose **MIDI Learn**. It pulses while it waits.
3. Move a knob or fader, or press a pad, on the controller. The two are now linked.
4. Right-click again to cancel, or choose **Clear MIDI Mapping** to remove a link.

The app recognises what the control is (a continuous knob, a button, an endless encoder, pitch bend). Min and max, invert, smoothing and **soft takeover** (so a knob does not make a value jump after a preset change) are set per mapping in the MIDI tab. Mappings live in a **profile** you can save and switch, so a home setup and a gig setup can differ.

Buttons learn the same way: right-click A, B, AUTO or TAP, then press a pad. In Edit, any slider can be learned. Details: [MIDI Hardware & Control Mappings](performance_controls.md#midi-hardware-control-mappings).

## OSC and TouchOSC

1. Enable **Preferences → OSC Controls → Enable OSC Server**. It listens on UDP port `8000` and sends feedback on `9000`; both can be changed.
2. In your app, set the target to this computer's IP address and the same ports.
3. Right-click a control in Liquid LSD, choose **OSC Learn**, then move a control in the app.

Macro knobs also answer `/macro/<bank>/knob/1` to `/8` with no mapping, and report their values back so a tablet layout stays in sync. OSC mappings are saved in profiles, one per app or layout. Details: [OSC / TouchOSC Control Surfaces](performance_controls.md#osc-touchosc-control-surfaces).
