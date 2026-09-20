# Performance Controls

This page covers two things that are especially useful during a live show: the trackpad performance surface, and the notes system for keeping reminders attached to your presets and parameters.

---

## Trackpad Performance Surface

If you're performing on a laptop, you can turn your trackpad into a hardware-style performance controller — a bit like the classic Stanton SCS.3m DJ mixer. Hold **`CapsLock`** to activate it.

While CapsLock is held:
- The trackpad stops moving the mouse cursor
- It becomes an absolute-position performance surface with three faders and a crossfader

Release CapsLock to return to normal mouse control.

### Surface layout

```
┌────────────────────────────────────────────────────┐
│  [Deck A Level]  [Deck BG Level]  [Deck B Level]   │  ← Top 55% of trackpad
│                                                    │
├────────────────────────────────────────────────────┤
│                   (dead zone)                      │  ← Middle buffer zone
├────────────────────────────────────────────────────┤
│              Crossfader  (A ←——→ B)                │  ← Bottom 28%
└────────────────────────────────────────────────────┘
```

**Bottom zone (crossfader):** Drag left for Deck A, right for Deck B. The full width maps to the crossfader range. Touch the centre and it snaps to 50/50.

**Top zone (level faders):** Three vertical strips — left third controls Deck A level, middle controls Deck BG, right controls Deck B. Touch anywhere in a strip to jump the level to that position instantly.

**Dead zone (middle):** A buffer between the two zones that prevents accidental switches when you're moving fast.

### Multi-finger stutters

The real power comes from using two fingers at once. Each zone tracks fingers independently, using a last-in-first-out stack:

**Crossfader cut stutters:**
1. Hold one finger down on the Deck A side.
2. Tap repeatedly with a second finger on the Deck B side.
3. Each tap snaps the crossfader to B; lifting the tap snaps it back to A.

This gives you machine-gun visual cuts synchronized to your tapping rhythm.

**Video strobes:**
1. Hold a finger at the bottom of a deck's level strip (level = 0%, blackout).
2. Tap with a second finger at the top of the same strip (level = 100%, full brightness).
3. Releasing the tap drops the deck back to blackout.

This creates rhythmic video flashes and stroboscopic accents.

When all fingers lift from a strip, the level stays frozen where you left it.

### Setup on Linux

On Linux, the trackpad surface reads raw multi-touch input directly. This requires read/write permission on the input device.

If the permission isn't set up, you'll see a **Touchpad Status: Read/Write Permission Required** message in **Preferences → General**, next to the **Enable CapsLock Trackpad Console** checkbox, with an **Install Permissions (Polkit)** button. Clicking it installs the necessary udev rule using `pkexec` — no reboot required. If you ran the desktop installer script (`./install-desktop.sh`) when you first unzipped the app, this was done automatically.

On macOS, native trackpad support works without any extra setup.

**Focus safety:** If Liquid LSD loses window focus (e.g. via Alt+Tab), the trackpad grab releases immediately, so you're never locked out of your mouse.

---

## Notes & Tooltips

### Hover tooltips

Hovering over almost any parameter in the app shows a tooltip with:
1. The parameter name, valid range, and factory default value.
2. A plain-English description of what the parameter does.
3. Any personal note you've attached to the parameter (in amber text).

Tooltips appear after a short delay (~250ms) so they don't flash annoyingly as you move the mouse.

### The three-tier notes system

You can attach notes at three different scopes:

| Scope | What it's for | Where it's stored | When it appears |
|-------|--------------|-------------------|-----------------|
| **Source Note** | Notes about a visual generator (e.g. "Gyroid works best with low gain feedback") | `~/.liquid-lsd/source-notes.json` | Stays with you globally, across all presets |
| **Preset Note** | Notes about a specific preset | Inside the `.lsd` file | Shows when hovering the preset name label on the deck monitor |
| **Parameter Note** | A reminder about a specific parameter in a preset | Inside the `.lsd` file | Shows in the parameter's hover tooltip |

Source Notes are global and persist no matter which preset you load — good for tips about generators that are always true. Preset and Parameter Notes travel with the preset file when you share it.

### Adding and editing notes

**Parameter notes:**
Hover over any parameter row in the Parameters panel and click **⋮** (or right-click the parameter name), then choose **Add/Edit Parameter Note...**

**Preset notes:**
Right-click the preset name label below the deck monitor preview and choose **Add/Edit Preset Note...**

**Source notes:**
Open the Deck Menu popup and choose **Add/Edit Source Note...**

All three open the same note editor: a multi-line text box up to 2048 characters. Press `Ctrl+Enter` to save, or `Escape` to cancel.

### The preset name label

Below each deck's monitor preview you'll see the name of the currently loaded preset. Hovering it shows the full file path, the last save time, and the preset note if one exists. A `*` next to the name means there are unsaved changes.

If no preset is loaded yet (the deck is in an unsaved state), the label shows `Untitled` in grey. You can't attach a preset note until the preset has been saved at least once.

---

## FX Chain & Slot Management

In the **Parameters** panel under the **`FX`** subtab, each deck provides a 4-slot modular ISF effects processor:

- **FX Chain Header Kebab (`⋮`)**:
  - **Save Chain As...**: Saves all 4 slots into an `.lsdfxchain` file in `library/fx_chains/`.
  - **Copy Chain / Paste Chain**: Copies or pastes all 4 slots across decks.
  - **Clear All Slots**: Disposes and empties all 4 slots on the active deck.
- **Per-Slot Kebab (`⋮`)**:
  - **Save Slot Preset As...**: Saves the slot's filter ID, dry/wet, and parameters into an `.lsdfx` file in `library/fx/`.
  - **Copy Slot / Paste Slot**: Copies or pastes individual slot configurations across slots or decks.
  - **Reset Slot**: Clears the slot filter and resets parameters.
- **Drag-and-Drop Targets**: Drag `.lsdfx` or `.lsdfxchain` files from the Library directly onto any slot to instantly swap or update effects.

---

## MIDI Hardware & Control Mappings

Liquid LSD includes an intelligent, multi-type MIDI subsystem for connecting hardware controllers (DJ controllers, fader banks, rotary knobs, pads, foot switches, and keyboards).

### Quick Setup

1. Connect your USB or DIN MIDI interface.
2. Open **Preferences → MIDI Controls** (or press `Ctrl+,`).
3. Ensure **Enable MIDI Subsystem** is checked. The background watchdog automatically detects newly connected controllers without restarting the application.
4. Check the **Live MIDI Monitor (Sniffer)** at the top of the panel: moving any knob or pressing any pad on your controller will immediately stream incoming packets (Channel, Message Type, Index, and Values).

### MIDI Learn & Modulator Variables

You can map any parameter, internal modulator variable, matrix CV modulator, or global performance action:

1. **In-Situ Right-Click Learn (Fastest)**:
   - Right-click any slider or variable label in the UI — including an LFO's **Speed / Subdivision**, **Depth**, **Min/Max bounds**, **Asymmetry (Slope)**, **Morph**, **Hold**, or a parameter's **Initial Range (Base Value)**.
   - Select **Learn MIDI (...)** from the context menu. The slider will pulse in cyan/blue while awaiting hardware input.
   - Move a knob, fader, or press a pad on your MIDI controller. Liquid LSD immediately binds the control, preserves your configured min/max limits, and saves the mapping to the active profile.
   - Right-click again and select **Cancel MIDI Learn** if needed.

2. **Modulation Matrix Cells**:
   - Click any cell in the MIDI column of the Parameters matrix, then click **Re-Learn MIDI** in Properties.

3. **Global Performance Actions**:
   - In **Preferences → MIDI Controls**, click **Learn** next to *Queue Advance A/B Next*, *Queue Step Back A/B Prev*, *BG Shader Advance*, *BG Step Back*, or *Tap Tempo*.

4. **Modulator Variable Paths**:
   - Modulator variables use unified path syntax: `<parameterPath>:mod/<modulatorIndex>/<propertyName>`, such as `Deck A/geometry/zoom:mod/0/subdivision` (LFO 1 Speed), `:mod/0/depth`, `:mod/0/slope`, or `:mod/1/morph`. Modulator mappings resolve dynamically each frame and display user-friendly labels (e.g. `Deck A/geometry/zoom [LFO 1 Speed]`) in the MIDI Preferences mapping table.

### Intelligent Signal Classification

When you turn a knob or tap a pad during Learn mode, Liquid LSD inspects the incoming stream and classifies it automatically:
- **Note On / Off:** Identified as a discrete button (pads/keys), defaulting to Momentary mode.
- **Continuous CC:** Identified as an absolute continuous pot or slider ($0 \dots 127$).
- **Relative Rotary Encoders:** Endless rotaries sending delta step packets ($63/65$ or $1/127$) are auto-classified into relative decoding mode.
- **Pitch Bend:** 14-bit bipolar inputs are mapped with center-spring return.

You can override any auto-detected mode at any time using the dropdowns in **Preferences → MIDI Controls**.

### Fine-Tuning Continuous Controls

In the **Parameter Mappings** table of the MIDI tab:
- **Min / Max Bounds:** Clamp physical travel to a specific sub-range of the parameter.
- **Invert:** Flips the physical direction ($0 \leftrightarrow 127$).
- **Slew (Smoothing):** Configurable smoothing filter ($0 \dots 250\,\text{ms}$) that eliminates 7-bit "zipper noise" or stepped visual jumps on sensitive shader uniforms.
- **Soft Takeover (Pickup):** Toggle between *Immediate* jump and *Soft Takeover*. With Soft Takeover active, parameter values will not jump upon loading a new preset until your physical knob/fader crosses the stored software value. The UI indicates `Awaiting Pickup` along with physical knob telemetry until synced.

### Discrete Controls (Buttons & Pads)

Hardware buttons and pads support four distinct trigger behaviors:
- **Momentary:** Active only while held; snaps back to minimum/default on release (ideal for video strobes, momentary feedback bursts, and glitch triggers).
- **Toggle (Latched):** Flips state on each press.
- **Step Increment:** Each press increments the parameter value by a configurable step size.
- **Step Decrement:** Each press decrements the parameter value by a configurable step size.

### Relative Rotary Encoders

Liquid LSD supports the three industry-standard relative encoding formats used by hardware like the DJ TechTools MIDI Fighter Twister, Behringer X-Touch, and Arturia controllers:
- **Binary Offset (64-centric):** $65 = +1, 63 = -1$.
- **Signed Bit (1-centric):** $1 = +1, 65 = -1$.
- **Two's Complement (1-centric):** $1 = +1, 127 = -1$.
Each rotary mapping includes an editable **Step** multiplier to dial in coarse or fine rotational sensitivity.

### Profiles

All assignments are stored in JSON profiles under `library/midi/<profile_name>.json`. You can create, save, switch, and delete mapping profiles in the MIDI tab, allowing you to quickly switch between different hardware setups (e.g. home studio controller vs live gig setup).

---

## OSC / TouchOSC Control Surfaces

Liquid LSD includes a native, zero-dependency OSC 1.0 server for wireless control from TouchOSC and other OSC-capable apps on phones and tablets — no external bridge software required.

### Quick Setup

1. Open **Preferences → OSC Controls** (or press `Ctrl+,`).
2. Check **Enable OSC Server**. By default it listens on UDP port `8000` and sends feedback out on port `9000` — both are editable, with a **Restart Server** button to apply port changes.
3. Point your TouchOSC layout at your computer's IP address, using the same inbound/outbound ports. Move any control on the layout — Liquid LSD automatically learns the client's IP from the first packet it receives, so there's nothing to configure on the desktop side beyond the ports.
4. The **Live Packet Sniffer** in the OSC Controls tab streams recent inbound and outbound traffic (address, arguments, and remote host) so you can confirm packets are arriving.

### OSC Learn & Modulator Variables

You can map OSC controls either in-situ from the UI or manually from Preferences:

1. **In-Situ Right-Click Learn (Fastest)**:
   - Right-click any slider or variable label in the UI — including an LFO's **Speed / Subdivision**, **Depth**, **Min/Max bounds**, **Asymmetry**, or a parameter's **Initial Range (Base Value)**.
   - Select **Learn OSC (...)** from the context menu. The slider will pulse in amber while awaiting input.
   - Move a fader, knob, or XY pad on your TouchOSC surface. Liquid LSD immediately binds the control, preserves your configured min/max limits, and saves the mapping to the active profile.
   - Right-click again and select **Cancel OSC Learn** (or cancel from Preferences) if needed.

2. **Manual Learn from Preferences**:
   - Open **Preferences → OSC Controls**.
   - Type or paste the target parameter or modulator path into the **Learn OSC** field:
     - Base parameters: `Mixer/crossfade`, `Deck A/geometry/zoom`, `Master/FX1/speed`.
     - Modulator variables: `Deck A/geometry/zoom:mod/0/subdivision` (LFO 1 Speed), `:mod/0/depth`, `:mod/0/slope`, `:mod/1/morph`, etc.
   - Click **Start Learn**, then move the control on your OSC surface.

3. **TouchOSC XY Pads & Multi-Argument Vectors**:
   - Multi-argument messages (e.g. `/2/xy`) are automatically unpacked into per-axis sub-addresses (`/2/xy/0` for X, `/2/xy/1` for Y). Learning from an XY pad binds the active axis.

### Fine-Tuning & Shaping

The **Address Mappings** table in the OSC Controls tab offers comprehensive per-control shaping:
- **Min / Max Clamping**: Scales the incoming controller range $[0.0, 1.0]$ to custom parameter boundaries (e.g., $0.1\,\text{s} \dots 8.0\,\text{s}$ for an LFO period).
- **Invert**: Inverts incoming values ($1.0 - x$).
- **Slew Rate Smoothing**: Exponential filter ($0 \dots 250\,\text{ms}$) to smooth out coarse touch faders or packet jitter without visual stepping.
- **Soft Takeover (Pickup)**: Prevents value jumping by waiting until the physical controller crosses the current software value before taking over control.

Macro Knobs also respond directly to `/macro/<bankId>/knob/1`–`/macro/<bankId>/knob/8` (no manual mapping needed), and broadcast their values back out over OSC whenever they change — keeping tablet layouts in bidirectional sync. See [Macro Controls & Performance Mode](macros_and_rack.md).

### Profiles

Like MIDI, OSC address mappings are stored in JSON profiles under `library/osc/<profile_name>.json`. Create, save, switch, and delete profiles from the OSC Controls tab to switch between different tablet layouts or venues.

